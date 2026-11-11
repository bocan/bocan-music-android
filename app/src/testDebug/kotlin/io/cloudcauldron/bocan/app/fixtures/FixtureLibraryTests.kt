package io.cloudcauldron.bocan.app.fixtures

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.app.demo.DemoAssetCorruptException
import io.cloudcauldron.bocan.app.demo.DemoLibrary
import io.cloudcauldron.bocan.app.demo.DirectoryDemoAssets
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.persistence.SyncApplier
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.model.LyricsKind
import io.cloudcauldron.bocan.persistence.model.PlayState
import io.cloudcauldron.bocan.persistence.model.PlaylistKind
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestCodec
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.engine.ArtworkStore
import io.cloudcauldron.bocan.sync.engine.MediaLayout
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixtureLibraryTests {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = BocanDatabase.createInMemory(context, Dispatchers.IO)
    private val applier = SyncApplier(db) { NOW }
    private val mediaLayout = MediaLayout(context)
    private val artworkStore = ArtworkStore(mediaLayout)
    private val dispatchers = CoroutineDispatchers(io = Dispatchers.IO, default = Dispatchers.IO)
    private val manifest = ManifestCodec.decode(File(FIXTURE_ASSET_DIR, "manifest.json").readText())

    private fun fixture(assets: DirectoryDemoAssets = DirectoryDemoAssets(FIXTURE_ASSET_DIR)) =
        FixtureLibrary(assets, DemoLibrary.Store(db, applier, mediaLayout, artworkStore), dispatchers, now = { NOW })

    @After
    fun tearDown() {
        db.close()
        mediaLayout.mediaRoot()?.deleteRecursively()
    }

    @Test
    fun `the unpaired demo profile writes nothing and leaves the demo seed to the app`() = runTest {
        fixture().seed(FixtureProfile.UnpairedDemo)

        assertNull(db.syncDao().server())
        assertTrue(db.syncDao().allTracks().isEmpty())
    }

    @Test
    fun `the empty paired profile pairs an unreachable Mac and loads no library`() = runTest {
        fixture().seed(FixtureProfile.EmptyPaired)

        val server = assertNotNull(db.syncDao().server())
        assertEquals(FixtureLibrary.SERVER_ID, server.serverId)
        assertEquals(FixtureLibrary.UNREACHABLE_FINGERPRINT, server.certFingerprint)
        assertTrue(db.syncDao().allTracks().isEmpty())
        assertTrue(db.syncDao().allEpisodes().isEmpty())
    }

    @Test
    fun `the paired library profile loads every surface the suite needs`() = runTest {
        fixture().seed(FixtureProfile.PairedLibrary)

        assertNotNull(db.syncDao().server())
        val tracks = db.syncDao().allTracks()
        assertEquals(48, tracks.size)
        assertTrue(tracks.all { it.id >= FixtureLibrary.ID_BASE && it.downloadState == DownloadState.Downloaded })
        assertEquals(18, db.libraryDao().observeAlbumsByName().first().size)
        assertEquals(14, db.libraryDao().observeArtists().first().size)

        val playlists = db.playlistDao().observePlaylistTree().first()
        assertEquals(setOf(PlaylistKind.Manual, PlaylistKind.Smart, PlaylistKind.Folder), playlists.map { it.kind }.toSet())

        assertEquals(2, db.syncDao().allPodcasts().size)
        val inProgress = assertNotNull(db.episodeStateDao().state("fixture-episode-2"))
        assertEquals(PlayState.InProgress, inProgress.playState)
        assertEquals(48_000L, inProgress.playPositionMs)

        val lyricKinds = manifest.tracks.mapNotNull { track -> db.lyricsDao().get(track.id)?.kind }
        assertEquals(4, lyricKinds.count { it == LyricsKind.Synced })
        assertEquals(2, lyricKinds.count { it == LyricsKind.Unsynced })

        manifest.tracks.forEach { track -> assertTrue(mediaLayout.trackFile(track.relPath).isFile, track.relPath) }
        manifest.episodes.forEach { episode -> assertTrue(mediaLayout.episodeFile(episode.relPath).isFile, episode.relPath) }
    }

    @Test
    fun `seeding twice is idempotent`() = runTest {
        fixture().seed(FixtureProfile.PairedLibrary)
        fixture().seed(FixtureProfile.PairedLibrary)

        assertEquals(48, db.syncDao().allTracks().size)
        assertEquals(3, db.syncDao().allEpisodes().size)
        assertNotNull(db.syncDao().server())
    }

    @Test
    fun `a corrupt fixture file fails the seed before any library row is written`() = runTest {
        val corrupt = "library/${manifest.tracks.first().relPath}"

        assertFailsWith<DemoAssetCorruptException> {
            fixture(DirectoryDemoAssets(FIXTURE_ASSET_DIR, corruptOn = corrupt)).seed(FixtureProfile.PairedLibrary)
        }
        assertTrue(db.syncDao().allTracks().isEmpty())
    }

    private companion object {
        /** Gradle runs unit tests with the module dir as the working dir. */
        val FIXTURE_ASSET_DIR = File("src/debug/assets/fixture")
        val NOW: Instant = Instant.parse("2026-11-11T12:00:00Z")
    }
}
