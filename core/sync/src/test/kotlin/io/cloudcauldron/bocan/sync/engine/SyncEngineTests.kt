package io.cloudcauldron.bocan.sync.engine

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.persistence.SyncApplier
import io.cloudcauldron.bocan.persistence.entities.PlayStatsEntity
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.model.manifest.Manifest
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestCodec
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestEpisode
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestTrack
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.identity.Fingerprints
import io.cloudcauldron.bocan.sync.net.TrustStore
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SyncEngineTests {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dispatchers = CoroutineDispatchers(io = Dispatchers.IO, default = Dispatchers.IO)

    private lateinit var db: BocanDatabase
    private lateinit var server: MockWebServer
    private lateinit var backend: FakeMac
    private lateinit var mediaLayout: MediaLayout

    @Before
    fun setUp() {
        db = BocanDatabase.createInMemory(context, Dispatchers.IO)
        server = MockWebServer()
        backend = FakeMac()
        server.dispatcher = backend
        server.start()
        mediaLayout = MediaLayout(context)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    @Test
    fun `fresh sync converges files database artwork then short-circuits`() = runBlocking {
        pair(generation = 0)
        val art = backend.artwork("a1")
        val t1 = backend.track(1, "Aphex/Album/01 One.flac", artworkHash = art)
        val t2 = backend.track(2, "Aphex/Album/02 Two.flac", artworkHash = art)
        val ep = backend.episode("ep1")
        backend.publish(manifest(generation = 7, tracks = listOf(t1, t2), episodes = listOf(ep)))

        val engine = engine()
        engine.syncNow()

        assertTrue(engine.state.value is SyncState.Done)
        val tracks = db.syncDao().allTracks()
        assertTrue(tracks.all { it.downloadState == DownloadState.Downloaded })
        assertEquals(backend.bytesOf("/v1/file/track/1"), mediaLayout.trackFile("Aphex/Album/01 One.flac").readBytes().toList())
        assertEquals(backend.bytesOf("/v1/file/episode/ep1"), mediaLayout.episodeFile("Podcasts/4/ep1.mp3").readBytes().toList())
        assertTrue(ArtworkStore(mediaLayout).fileFor(art).isFile)
        assertEquals(DownloadState.Downloaded, db.syncDao().allEpisodes().single().downloadState)

        // A second sync at the same generation with nothing pending does no work.
        backend.clearRequests()
        engine.syncNow()
        assertTrue(engine.state.value is SyncState.Done)
        assertEquals(0, backend.count("/v1/manifest"))
    }

    @Test
    fun `an interrupted transfer pauses and resumes without re-downloading completed files`() = runBlocking {
        pair(generation = 0)
        val t1 = backend.track(1, "A/Album/01.flac")
        val t2 = backend.track(2, "A/Album/02.flac")
        backend.publish(manifest(generation = 3, tracks = listOf(t1, t2)))
        backend.stallOnce("/v1/file/track/2")

        val engine = engine(readTimeoutMs = 500)
        engine.syncNow()
        assertTrue(engine.state.value is SyncState.ServerUnreachable)
        assertTrue(mediaLayout.trackFile("A/Album/01.flac").isFile)
        assertTrue(db.syncDao().allTracks().isEmpty()) // apply never ran

        engine.syncNow()

        assertTrue(engine.state.value is SyncState.Done)
        assertTrue(db.syncDao().allTracks().all { it.downloadState == DownloadState.Downloaded })
        assertEquals("track 1 must not be re-downloaded", 1, backend.count("/v1/file/track/1"))
    }

    @Test
    fun `a wiped media root re-downloads files and artwork on the next sync`() = runBlocking {
        pair(generation = 0)
        val art = backend.artwork("a1")
        val t1 = backend.track(1, "A/Album/01.flac", artworkHash = art)
        backend.publish(manifest(generation = 4, tracks = listOf(t1), episodes = listOf(backend.episode("ep1"))))
        val engine = engine()
        engine.syncNow()
        assertTrue(engine.state.value is SyncState.Done)

        // Remove all synced media: files gone, rows flipped back to pending, pairing intact.
        mediaLayout.mediaRoot()!!.deleteRecursively()
        db.syncDao().setTrackDownloadState(listOf(1L), DownloadState.Pending)
        db.syncDao().setEpisodeDownloadState(listOf("ep1"), DownloadState.Pending)

        engine.syncNow()

        assertTrue(engine.state.value is SyncState.Done)
        assertTrue(mediaLayout.trackFile("A/Album/01.flac").isFile)
        assertTrue(mediaLayout.episodeFile("Podcasts/4/ep1.mp3").isFile)
        assertTrue(ArtworkStore(mediaLayout).fileFor(art).isFile)
        assertTrue(db.syncDao().allTracks().all { it.downloadState == DownloadState.Downloaded })
        assertEquals(DownloadState.Downloaded, db.syncDao().allEpisodes().single().downloadState)
    }

    @Test
    fun `a departed track is deleted from disk and db after apply while play stats survive`() = runBlocking {
        pair(generation = 0)
        val keep = backend.track(1, "A/Album/01.flac")
        val leaving = backend.track(2, "A/Album/02.flac")
        backend.publish(manifest(generation = 1, tracks = listOf(keep, leaving)))
        engine().syncNow()
        db.playStatsDao().upsert(PlayStatsEntity(trackId = 2, playCount = 9))
        val leavingFile = mediaLayout.trackFile("A/Album/02.flac")
        assertTrue(leavingFile.isFile)

        backend.publish(manifest(generation = 2, tracks = listOf(keep)))
        engine().syncNow()

        assertEquals(listOf(1L), db.syncDao().allTracks().map { it.id })
        assertFalse(leavingFile.exists())
        assertEquals(9L, db.playStatsDao().stats(2)?.playCount)
    }

    @Test
    fun `insufficient space refuses to start transfers`() = runBlocking {
        pair(generation = 0)
        val t1 = backend.track(1, "A/Album/01.flac")
        backend.publish(manifest(generation = 1, tracks = listOf(t1)))

        val engine = engine(freeSpaceBytes = { 1L })
        engine.syncNow()
        // (freeSpaceBytes forced to 1 byte so the pending transfer cannot fit.)

        val failed = engine.state.value as SyncState.Failed
        assertTrue(failed.error is SyncError.InsufficientStorage)
        assertEquals(0, backend.count("/v1/file/track/1"))
        assertTrue(db.syncDao().allTracks().isEmpty())
    }

    @Test
    fun `a path traversal manifest fails safe without touching disk or database`() = runBlocking {
        pair(generation = 0)
        val evil = backend.track(1, "../../evil.flac")
        backend.publish(manifest(generation = 1, tracks = listOf(evil)))

        val engine = engine()
        engine.syncNow()

        val failed = engine.state.value as SyncState.Failed
        assertTrue(failed.error is SyncError.UnsafePath)
        assertEquals(0, backend.count("/v1/file/track/1"))
        assertTrue(db.syncDao().allTracks().isEmpty())
    }

    @Test
    fun `a stale file refetches the manifest and still converges`() = runBlocking {
        pair(generation = 0)
        val t1 = backend.track(1, "A/Album/01.flac")
        backend.publish(manifest(generation = 5, tracks = listOf(t1)))
        backend.staleOnce("/v1/file/track/1")

        val engine = engine()
        engine.syncNow()

        assertTrue(engine.state.value is SyncState.Done)
        assertTrue(db.syncDao().allTracks().single().downloadState == DownloadState.Downloaded)
        assertEquals("manifest fetched once for the run, once for the stale refetch", 2, backend.count("/v1/manifest"))
    }

    // --- harness ---

    private suspend fun pair(generation: Long) {
        TrustStore(db.syncDao()).save(
            SyncServerEntity(
                serverId = SERVER_ID,
                serverName = "Test Mac",
                certFingerprint = "ab".repeat(32),
                certDer = byteArrayOf(1),
                lastAppliedGeneration = generation,
                lastSyncAt = null,
                pairedAt = Instant.EPOCH
            )
        )
    }

    private fun engine(readTimeoutMs: Long = 30_000, freeSpaceBytes: (() -> Long?)? = null): SyncEngine {
        val client = OkHttpClient.Builder().readTimeout(readTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        val base = server.url("/").toString().toHttpUrl()
        return SyncEngine(
            trustStore = { db.syncDao().server() },
            transport = SyncTransport(
                endpoints = { base },
                api = SyncApi(client, dispatchers),
                transferrer = FileTransferrer(Downloader(client, dispatchers), mediaLayout, ArtworkStore(mediaLayout), dispatchers)
            ),
            store = SyncStore(
                applier = SyncApplier(db) { FIXED_NOW },
                mediaLayout = mediaLayout,
                pendingDownloads = { db.libraryDao().observeDownloadCounts().first().pending },
                artworkPresent = { ArtworkStore(mediaLayout).existing(it) != null }
            ),
            dispatchers = dispatchers,
            scope = CoroutineScope(dispatchers.default),
            config = SyncConfig(now = { FIXED_NOW }, freeSpaceBytes = freeSpaceBytes)
        )
    }

    private fun manifest(generation: Long, tracks: List<ManifestTrack>, episodes: List<ManifestEpisode> = emptyList()): Manifest = Manifest(
        protocolVersion = 1,
        serverId = SERVER_ID,
        serverName = "Test Mac",
        generation = generation,
        generatedAt = "2026-07-10T12:00:00Z",
        tracks = tracks,
        episodes = episodes
    )

    /** A minimal in-process stand-in for the Mac's sync server. */
    private class FakeMac : Dispatcher() {
        private val files = HashMap<String, ByteArray>()
        private val requests = ConcurrentLinkedQueue<String>()
        private val stallOnce = HashSet<String>()
        private val staleOnce = HashSet<String>()

        @Volatile private var manifestJson: String = ""

        @Volatile private var generation: Long = 0

        fun publish(manifest: Manifest) {
            generation = manifest.generation
            manifestJson = ManifestCodec.encode(manifest)
        }

        fun track(id: Long, relPath: String, artworkHash: String? = null): ManifestTrack {
            val bytes = ByteArray(120_000) { ((it * 7 + id) and 0xFF).toByte() }
            files["/v1/file/track/$id"] = bytes
            return ManifestTrack(
                id = id,
                relPath = relPath,
                size = bytes.size.toLong(),
                sha256 = Fingerprints.sha256Hex(bytes),
                format = "flac",
                durationMs = 60_000,
                title = "Track $id",
                artist = "Artist",
                artistId = 1,
                albumArtist = "Artist",
                albumArtistId = 1,
                album = "Album",
                albumId = 1,
                trackNumber = id.toInt(),
                isLossless = true,
                artworkHash = artworkHash
            )
        }

        fun episode(id: String, podcastId: Long = 4): ManifestEpisode {
            val bytes = ByteArray(40_000) { ((it * 3) and 0xFF).toByte() }
            files["/v1/file/episode/$id"] = bytes
            return ManifestEpisode(
                id = id,
                podcastId = podcastId,
                guid = "guid-$id",
                title = "Episode $id",
                publishedAt = "2026-06-01T09:00:00Z",
                durationMs = 3_600_000,
                relPath = "Podcasts/$podcastId/$id.mp3",
                size = bytes.size.toLong(),
                sha256 = Fingerprints.sha256Hex(bytes)
            )
        }

        fun artwork(seed: String): String {
            val bytes = ByteArray(2_000) { ((it + seed.hashCode()) and 0xFF).toByte() }
            val hash = Fingerprints.sha256Hex(bytes)
            files["/v1/artwork/$hash"] = bytes
            return hash
        }

        fun bytesOf(path: String): List<Byte> = files.getValue(path).toList()

        fun count(path: String): Int = requests.count { it == path }

        fun clearRequests() = requests.clear()

        fun stallOnce(path: String) {
            stallOnce.add(path)
        }

        fun staleOnce(path: String) {
            staleOnce.add(path)
        }

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.encodedPath
            requests.add(path)
            return when {
                path == "/v1/ping" -> json("""{"protocolVersion":1,"serverId":"$SERVER_ID","generation":$generation}""")
                path == "/v1/manifest" -> json(manifestJson)
                path in files -> serveFile(path, request)
                else -> MockResponse.Builder().code(404).build()
            }
        }

        private fun serveFile(path: String, request: RecordedRequest): MockResponse = when {
            stallOnce.remove(path) -> MockResponse.Builder().onResponseStart(SocketEffect.Stall).build()
            staleOnce.remove(path) -> MockResponse.Builder().code(412).build()
            else -> serveBytes(files.getValue(path), request.headers["Range"])
        }

        private fun serveBytes(bytes: ByteArray, range: String?): MockResponse {
            if (range == null) return MockResponse.Builder().code(200).body(Buffer().write(bytes)).build()
            val start = range.removePrefix("bytes=").removeSuffix("-").toInt()
            return MockResponse.Builder()
                .code(206)
                .addHeader("Content-Range", "bytes $start-${bytes.size - 1}/${bytes.size}")
                .body(Buffer().write(bytes.copyOfRange(start, bytes.size)))
                .build()
        }

        private fun json(body: String): MockResponse =
            MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()
    }

    private companion object {
        const val SERVER_ID = "server-1"
        val FIXED_NOW: Instant = Instant.parse("2026-07-10T12:00:00Z")
    }
}
