package io.cloudcauldron.bocan.scrobble.providers

import io.cloudcauldron.bocan.scrobble.CoroutineDispatchers
import io.cloudcauldron.bocan.scrobble.FakeTokenStore
import io.cloudcauldron.bocan.scrobble.PlayEvent
import io.cloudcauldron.bocan.scrobble.ScrobbleError
import io.cloudcauldron.bocan.scrobble.SubmissionOutcome
import io.cloudcauldron.bocan.scrobble.auth.TokenKeys
import io.cloudcauldron.bocan.scrobble.net.ScrobbleHttp
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListenBrainzCompatibleProviderTests {
    private fun listenBrainz(server: MockWebServer, tokens: FakeTokenStore): ListenBrainzProvider {
        val dispatcher = UnconfinedTestDispatcher()
        val http = ScrobbleHttp(OkHttpClient(), CoroutineDispatchers(io = dispatcher, default = dispatcher))
        return ListenBrainzProvider(tokens, http, baseUrl = server.url("/").toString().toHttpUrl())
    }

    @Test
    fun `a single listen submits with a bearer token`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("{\"status\":\"ok\"}").build())
            val store = FakeTokenStore(mapOf(TokenKeys.LISTENBRAINZ_TOKEN to "TK"))

            val outcome = listenBrainz(server, store).scrobble(listOf(play(1))).single().outcome

            assertEquals(SubmissionOutcome.Success, outcome)
            val recorded = server.takeRequest()
            assertEquals("Token TK", recorded.headers["Authorization"])
            assertEquals("/1/submit-listens", recorded.target)
            val body = recorded.body?.utf8().orEmpty()
            assertTrue(body.contains("\"listen_type\":\"single\""))
            assertTrue(body.contains("\"track_name\":\"Track 1\""))
            assertTrue(body.contains("\"listened_at\""))
        }
    }

    @Test
    fun `now playing omits the listened_at timestamp`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("{}").build())
            listenBrainz(server, FakeTokenStore(mapOf(TokenKeys.LISTENBRAINZ_TOKEN to "TK"))).updateNowPlaying(play(1))
            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body.contains("\"listen_type\":\"playing_now\""))
            assertTrue(!body.contains("listened_at"))
        }
    }

    @Test
    fun `401 is auth-expired`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(401).build())
            val store = FakeTokenStore(mapOf(TokenKeys.LISTENBRAINZ_TOKEN to "TK"))
            assertEquals(SubmissionOutcome.AuthExpired, listenBrainz(server, store).scrobble(listOf(play(1))).single().outcome)
        }
    }

    @Test
    fun `429 retries with the retry-after delay`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())
            val store = FakeTokenStore(mapOf(TokenKeys.LISTENBRAINZ_TOKEN to "TK"))
            val outcome = listenBrainz(server, store).scrobble(listOf(play(1))).single().outcome
            assertEquals(SubmissionOutcome.Retry("rate limited", 30L), outcome)
        }
    }

    @Test
    fun `500 is retryable`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(500).build())
            val store = FakeTokenStore(mapOf(TokenKeys.LISTENBRAINZ_TOKEN to "TK"))
            assertTrue(listenBrainz(server, store).scrobble(listOf(play(1))).single().outcome is SubmissionOutcome.Retry)
        }
    }

    @Test
    fun `no token yields auth-expired for a batch and throws for now-playing`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val provider = listenBrainz(server, FakeTokenStore())
            assertEquals(SubmissionOutcome.AuthExpired, provider.scrobble(listOf(play(1))).single().outcome)
            assertFailsWith<ScrobbleError.NotAuthenticated> { provider.updateNowPlaying(play(1)) }
        }
    }

    @Test
    fun `rocksky posts to its endpoint with the same protocol`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("{}").build())
            val dispatcher = UnconfinedTestDispatcher()
            val http = ScrobbleHttp(OkHttpClient(), CoroutineDispatchers(io = dispatcher, default = dispatcher))
            val provider =
                RockskyProvider(FakeTokenStore(mapOf(TokenKeys.ROCKSKY_KEY to "RK")), http, server.url("/").toString().toHttpUrl())

            assertEquals(SubmissionOutcome.Success, provider.scrobble(listOf(play(1))).single().outcome)
            val recorded = server.takeRequest()
            assertEquals("Token RK", recorded.headers["Authorization"])
            assertEquals("/1/submit-listens", recorded.target)
            assertEquals(ProviderId.ROCKSKY, provider.id)
        }
    }

    private fun play(trackId: Long) = PlayEvent(
        trackId = trackId,
        title = "Track $trackId",
        artist = "Artist",
        album = "Album",
        albumArtist = "Album Artist",
        durationSec = 200,
        playedAtEpochSec = 1_700_000_000 + trackId,
        queueId = trackId
    )
}
