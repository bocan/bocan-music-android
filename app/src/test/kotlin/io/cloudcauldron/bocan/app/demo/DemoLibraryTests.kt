package io.cloudcauldron.bocan.app.demo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.persistence.SyncApplier
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.model.manifest.Manifest
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestCodec
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestTrack
import io.cloudcauldron.bocan.playback.CoroutineDispatchers as PlaybackDispatchers
import io.cloudcauldron.bocan.playback.lyrics.FetchResult
import io.cloudcauldron.bocan.playback.lyrics.LyricsDoc
import io.cloudcauldron.bocan.playback.lyrics.LyricsRepository
import io.cloudcauldron.bocan.playback.lyrics.LyricsResult
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.engine.ArtworkStore
import io.cloudcauldron.bocan.sync.engine.MediaLayout
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class DemoLibraryTests {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = BocanDatabase.createInMemory(context, Dispatchers.IO)
    private val applier = SyncApplier(db) { NOW }
    private val mediaLayout = MediaLayout(context)
    private val artworkStore = ArtworkStore(mediaLayout)
    private val prefs = FakeDemoPreferences()
    private val dispatchers = CoroutineDispatchers(io = Dispatchers.IO, default = Dispatchers.IO)
    private val demoManifest: Manifest = ManifestCodec.decode(File(DEMO_ASSET_DIR, "manifest.json").readText())

    private fun library(assets: DemoAssets = DirectoryDemoAssets()) =
        DemoLibrary(assets, DemoLibrary.Store(db, applier, mediaLayout, artworkStore), prefs, dispatchers, now = { NOW })

    @After
    fun tearDown() {
        db.close()
        mediaLayout.mediaRoot()?.deleteRecursively()
    }

    @Test
    fun `eligible only when unpaired and empty`() = runTest {
        val demo = library()
        assertTrue(demo.isEligible())

        db.syncDao().insertServer(pairedServer())
        assertFalse(demo.isEligible())
        db.syncDao().clearServer()

        applier.apply(realManifest())
        assertFalse(demo.isEligible())
        assertFalse(demo.isActive())
    }

    @Test
    fun `seed writes rows files artwork and lyrics through the sync path`() = runTest {
        val result = library().seed()
        assertEquals(SeedResult.Seeded, result)

        val tracks = db.syncDao().allTracks().sortedBy { it.id }
        assertEquals(demoManifest.tracks.map { it.id }, tracks.map { it.id })
        assertTrue(tracks.all { it.downloadState == DownloadState.Downloaded })
        assertEquals(1, db.libraryDao().observeAlbumsByName().first().size)
        assertEquals(1, db.libraryDao().observeArtists().first().size)

        val playlists = db.playlistDao().observePlaylistTree().first()
        assertEquals(demoManifest.playlists.map { it.name }.toSet(), playlists.map { it.name }.toSet())
        demoManifest.playlists.forEach { playlist ->
            val members = db.playlistDao().observeTracksIn(playlist.id).first().map { it.id }
            assertEquals(playlist.trackIds, members)
        }

        demoManifest.tracks.forEach { track ->
            val file = mediaLayout.trackFile(track.relPath)
            assertTrue(file.isFile, track.relPath)
            assertEquals(track.sha256, sha256(file))
            assertEquals(track.lyricsHash, db.lyricsDao().get(track.id)?.lyricsHash)
        }
        val hashes = demoManifest.tracks.mapNotNull { it.artworkHash }.toSet()
        hashes.forEach { hash -> assertEquals(hash, sha256(artworkStore.fileFor(hash))) }
        assertTrue(library().isActive())
    }

    @Test
    fun `seeded lyrics serve the synced documents with no Mac`() = runTest {
        library().seed()
        val repository = LyricsRepository(
            lyricsDao = db.lyricsDao(),
            fetcher = { FetchResult.Unreachable },
            dispatchers = PlaybackDispatchers(io = Dispatchers.IO, default = Dispatchers.IO, main = Dispatchers.IO),
            log = AppLog.forCategory(LogCategory.Playback),
            now = { NOW }
        )

        val docs = demoManifest.tracks.map { track ->
            val loaded = assertIs<LyricsResult.Loaded>(repository.lyricsFor(track.id, track.lyricsHash))
            val synced = assertIs<LyricsDoc.Synced>(loaded.doc)
            assertTrue(synced.lines.size >= MIN_TIMED_LINES, "track ${track.id} has ${synced.lines.size} lines")
            synced
        }
        assertNotEquals(docs[0], docs[1])
    }

    @Test
    fun `a second seed is a no-op while active`() = runTest {
        val demo = library()
        assertEquals(SeedResult.Seeded, demo.seed())
        val before = db.syncDao().allTracks().map { it.id to it.syncedAt }
        val mtimes = demoManifest.tracks.map { mediaLayout.trackFile(it.relPath).lastModified() }

        assertEquals(SeedResult.AlreadyActive, demo.seed())

        assertEquals(before, db.syncDao().allTracks().map { it.id to it.syncedAt })
        assertEquals(mtimes, demoManifest.tracks.map { mediaLayout.trackFile(it.relPath).lastModified() })
    }

    @Test
    fun `first launch seeds once and never again`() = runTest {
        val demo = library()
        demo.seedOnFirstLaunch()
        assertTrue(demo.isActive())
        assertTrue(prefs.autoSeeded.value)

        demo.clear()
        assertTrue(demo.isEligible())
        demo.seedOnFirstLaunch()
        assertFalse(demo.isActive())
    }

    @Test
    fun `first launch on a paired phone sets the flag without seeding`() = runTest {
        db.syncDao().insertServer(pairedServer())
        library().seedOnFirstLaunch()
        assertTrue(prefs.autoSeeded.value)
        assertTrue(db.syncDao().allTracks().isEmpty())
    }

    @Test
    fun `a real manifest lists the demo as departed and apply removes every demo row`() = runTest {
        library().seed()
        val real = realManifest()

        val plan = applier.plan(real)
        assertEquals(demoManifest.tracks.map { it.relPath }.sorted(), plan.relPathsToDelete)

        db.syncDao().insertServer(pairedServer())
        applier.apply(real)
        assertTrue(db.syncDao().allTracks().none { DemoLibrary.isDemoTrackId(it.id) })
        assertTrue(db.libraryDao().observeAlbumsByName().first().none { DemoLibrary.isDemoTrackId(it.id) })
        assertTrue(db.libraryDao().observeArtists().first().none { DemoLibrary.isDemoTrackId(it.id) })
        assertTrue(db.playlistDao().observePlaylistTree().first().none { DemoLibrary.isDemoTrackId(it.id) })
        assertFalse(library().isActive())
    }

    @Test
    fun `clear removes rows files covers lyrics and the folder`() = runTest {
        val demo = library()
        demo.seed()

        demo.clear()

        assertTrue(db.syncDao().allTracks().isEmpty())
        assertTrue(db.playlistDao().observePlaylistTree().first().isEmpty())
        demoManifest.tracks.forEach { track ->
            assertFalse(mediaLayout.trackFile(track.relPath).exists(), track.relPath)
            assertNull(db.lyricsDao().get(track.id))
        }
        demoManifest.tracks.mapNotNull { it.artworkHash }.forEach { hash -> assertNull(artworkStore.existing(hash)) }
        val demoDir = File(checkNotNull(mediaLayout.mediaRoot()), "library/Demo")
        assertFalse(demoDir.exists())
        assertTrue(demo.isEligible())
    }

    @Test
    fun `clear is a no-op when the demo is not active`() = runTest {
        applier.apply(realManifest())
        library().clear()
        assertEquals(1, db.syncDao().allTracks().size)
    }

    @Test
    fun `an asset that fails to open leaves no rows`() = runTest {
        val failing = DirectoryDemoAssets(failOn = "library/${demoManifest.tracks[1].relPath}")
        val result = library(failing).seed()

        assertIs<SeedResult.Failed>(result)
        assertTrue(db.syncDao().allTracks().isEmpty())
        assertTrue(library().isEligible())
    }

    @Test
    fun `a corrupt asset fails the hash check and leaves no rows or part files`() = runTest {
        val corrupt = DirectoryDemoAssets(corruptOn = "library/${demoManifest.tracks[0].relPath}")
        val result = library(corrupt).seed()

        val failure = assertIs<SeedResult.Failed>(result)
        assertIs<DemoAssetCorruptException>(failure.error)
        assertTrue(db.syncDao().allTracks().isEmpty())
        val target = mediaLayout.trackFile(demoManifest.tracks[0].relPath)
        assertFalse(File(target.path + ".part").exists())
    }

    @Test
    fun `demo ids sit above every real id`() {
        assertTrue(DemoLibrary.isDemoTrackId(DemoLibrary.ID_BASE + 1))
        assertFalse(DemoLibrary.isDemoTrackId(1))
        assertFalse(DemoLibrary.isDemoTrackId(DemoLibrary.ID_BASE - 1))
    }

    private fun realManifest(): Manifest = Manifest(
        protocolVersion = 1,
        serverId = "real-mac",
        serverName = "Real Mac",
        generation = 1,
        generatedAt = NOW.toString(),
        tracks = listOf(
            ManifestTrack(
                id = 1,
                relPath = "Rush/Signals/01 Subdivisions.flac",
                size = 1000,
                sha256 = "ab".repeat(32),
                format = "flac",
                durationMs = 334_000,
                title = "Subdivisions",
                artist = "Rush",
                artistId = 1,
                albumArtist = "Rush",
                albumArtistId = 1,
                album = "Signals",
                albumId = 1
            )
        )
    )

    private fun pairedServer() = SyncServerEntity(
        serverId = "real-mac",
        serverName = "Real Mac",
        certFingerprint = "cd".repeat(32),
        certDer = byteArrayOf(1),
        lastAppliedGeneration = 0,
        lastSyncAt = null,
        pairedAt = NOW
    )

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private class FakeDemoPreferences : DemoPreferencesSource {
        override val autoSeeded = MutableStateFlow(false)

        override suspend fun setAutoSeeded() {
            autoSeeded.value = true
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-07T12:00:00Z")
        const val MIN_TIMED_LINES = 12
    }
}
