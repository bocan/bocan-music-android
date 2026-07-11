package io.cloudcauldron.bocan.sync.engine

import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.identity.Fingerprints
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The single-file downloader against a local MockWebServer (never the network). */
class DownloaderTests {
    @get:Rule val temp = TemporaryFolder()

    private lateinit var server: MockWebServer

    private val payload = ByteArray(300_000) { (it * 31 + 7).toByte() }
    private val payloadSha = Fingerprints.sha256Hex(payload)
    private val dispatchers = CoroutineDispatchers(io = Dispatchers.IO)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun downloader(readTimeoutMs: Long = 30_000): Downloader =
        Downloader(OkHttpClient.Builder().readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS).build(), dispatchers)

    private fun url() = server.url("/v1/file/track/1").toString().toHttpUrl()

    private fun target() = File(temp.root, "01 Title.flac")

    private fun partOf(target: File) = File(target.parentFile, target.name + ".part")

    @Test
    fun `full download verifies the digest and leaves no part file`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(payload)).build())
        val target = target()

        val result = downloader().download(url(), payloadSha, target)

        assertEquals(Downloader.Result.Downloaded, result)
        assertArrayed(payload, target.readBytes())
        assertFalse("part left behind", partOf(target).exists())
    }

    @Test
    fun `an already present verified file needs no request`() = runTest {
        val target = target()
        target.writeBytes(payload)

        val result = downloader().download(url(), payloadSha, target)

        assertEquals(Downloader.Result.AlreadyPresent, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a preexisting part resumes with a range request and completes`() = runTest {
        // A prior interrupted attempt left the first third on disk.
        val target = target()
        val prefixLength = 100_000
        partOf(target).writeBytes(payload.copyOf(prefixLength))
        server.dispatcher = rangeAwareDispatcher()

        val result = downloader().download(url(), payloadSha, target)

        assertEquals(Downloader.Result.Downloaded, result)
        assertArrayed(payload, target.readBytes())
        val recorded = server.takeRequest()
        assertEquals("bytes=$prefixLength-", recorded.headers["Range"])
        assertEquals(payloadSha, recorded.headers["If-Match"])
    }

    @Test
    fun `a complete part that missed its rename is finished without a request`() = runTest {
        // A prior run wrote every byte but died before the atomic rename.
        val target = target()
        partOf(target).writeBytes(payload)

        val result = downloader().download(url(), payloadSha, target)

        assertEquals(Downloader.Result.Downloaded, result)
        assertArrayed(payload, target.readBytes())
        assertFalse("part left behind", partOf(target).exists())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a 416 discards the part and retries cleanly from the start`() = runTest {
        // Full-length garbage: resuming would ask for a range past the file's end.
        val target = target()
        partOf(target).writeBytes(ByteArray(payload.size) { 1 })
        server.enqueue(MockResponse.Builder().code(416).build())
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(payload)).build())

        val result = downloader().download(url(), payloadSha, target)

        assertEquals(Downloader.Result.Downloaded, result)
        assertArrayed(payload, target.readBytes())
        assertFalse("part left behind", partOf(target).exists())
        assertEquals("bytes=${payload.size}-", server.takeRequest().headers["Range"])
        assertEquals(null, server.takeRequest().headers["Range"])
    }

    @Test
    fun `a corrupt body is retried once then recorded as a failure with no file in place`() = runTest {
        val wrong = ByteArray(payload.size) { 0 }
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(wrong)).build())
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(wrong)).build())
        val target = target()

        val result = downloader().download(url(), payloadSha, target)

        assertTrue(result is Downloader.Result.Failed)
        assertFalse(target.exists())
        assertFalse(partOf(target).exists())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a 412 surfaces ManifestStale`() = runTest {
        server.enqueue(MockResponse.Builder().code(412).build())

        try {
            downloader().download(url(), payloadSha, target())
            throw AssertionError("expected ManifestStale")
        } catch (e: SyncError.ManifestStale) {
            assertTrue(e.url.contains("/v1/file/track/1"))
        }
    }

    @Test
    fun `a stalled server aborts within the read window and leaves the sync resumable`() = runTest {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())
        val target = target()

        try {
            downloader(readTimeoutMs = 500).download(url(), payloadSha, target)
            throw AssertionError("expected a network abort")
        } catch (e: SyncError.Network) {
            // The target was never created, so the next sync simply re-requests it.
            assertTrue(e.url!!.contains("/v1/file/track/1"))
            assertFalse(target.exists())
        }
    }

    private fun rangeAwareDispatcher(): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val range = request.headers["Range"]
            if (range == null) {
                return MockResponse.Builder().code(200).body(Buffer().write(payload)).build()
            }
            val start = range.removePrefix("bytes=").removeSuffix("-").toInt()
            val slice = payload.copyOfRange(start, payload.size)
            return MockResponse.Builder()
                .code(206)
                .addHeader("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
                .body(Buffer().write(slice))
                .build()
        }
    }

    private fun assertArrayed(expected: ByteArray, actual: ByteArray) {
        assertEquals(Fingerprints.sha256Hex(expected), Fingerprints.sha256Hex(actual))
    }
}
