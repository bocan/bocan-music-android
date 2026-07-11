package io.cloudcauldron.bocan.scrobble.providers

import io.cloudcauldron.bocan.scrobble.CoroutineDispatchers
import io.cloudcauldron.bocan.scrobble.FakeTokenStore
import io.cloudcauldron.bocan.scrobble.PlayEvent
import io.cloudcauldron.bocan.scrobble.SubmissionOutcome
import io.cloudcauldron.bocan.scrobble.auth.TokenKeys
import io.cloudcauldron.bocan.scrobble.net.ScrobbleHttp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LastFmProviderTests {
    private val config = LastFmConfig(apiKey = "KEY", sharedSecret = "SECRET")

    private fun provider(server: MockWebServer, tokens: FakeTokenStore): LastFmProvider {
        val dispatcher = UnconfinedTestDispatcher()
        val http = ScrobbleHttp(OkHttpClient(), CoroutineDispatchers(io = dispatcher, default = dispatcher))
        return LastFmProvider(config.copy(endpoint = server.url("/2.0/")), tokens, http)
    }

    @Test
    fun `a batch signs and scrobbles successfully`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("{\"scrobbles\":{}}").build())
            val store = FakeTokenStore(mapOf(TokenKeys.LAST_FM_SESSION to "SESSION"))

            val results = provider(server, store).scrobble(listOf(play(1), play(2)))

            assertEquals(listOf(SubmissionOutcome.Success, SubmissionOutcome.Success), results.map { it.outcome })
            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body.contains("method=track.scrobble"))
            assertTrue(body.contains("api_sig="))
            assertTrue(body.contains("artist%5B0%5D") || body.contains("artist[0]"))
        }
    }

    @Test
    fun `a 503 is retryable`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(503).build())
            val store = FakeTokenStore(mapOf(TokenKeys.LAST_FM_SESSION to "SESSION"))
            val outcome = provider(server, store).scrobble(listOf(play(1))).single().outcome
            assertTrue(outcome is SubmissionOutcome.Retry)
        }
    }

    @Test
    fun `lastfm error code 9 is auth-expired`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("{\"error\":9,\"message\":\"Invalid session key\"}").build())
            val store = FakeTokenStore(mapOf(TokenKeys.LAST_FM_SESSION to "SESSION"))
            assertEquals(SubmissionOutcome.AuthExpired, provider(server, store).scrobble(listOf(play(1))).single().outcome)
        }
    }

    @Test
    fun `lastfm error code 16 is retryable`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("{\"error\":16,\"message\":\"temporary\"}").build())
            val store = FakeTokenStore(mapOf(TokenKeys.LAST_FM_SESSION to "SESSION"))
            assertTrue(provider(server, store).scrobble(listOf(play(1))).single().outcome is SubmissionOutcome.Retry)
        }
    }

    @Test
    fun `no session key yields auth-expired without a network call`() = runTest {
        MockWebServer().use { server ->
            server.start()
            assertEquals(SubmissionOutcome.AuthExpired, provider(server, FakeTokenStore()).scrobble(listOf(play(1))).single().outcome)
        }
    }

    @Test
    fun `completing auth stores the session key and username`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("{\"session\":{\"name\":\"alice\",\"key\":\"THE_SK\"}}").build())
            val store = FakeTokenStore()
            provider(server, store).completeAuth("token")
            assertEquals("THE_SK", store.get(TokenKeys.LAST_FM_SESSION))
            assertEquals("alice", store.get(TokenKeys.LAST_FM_USERNAME))
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
