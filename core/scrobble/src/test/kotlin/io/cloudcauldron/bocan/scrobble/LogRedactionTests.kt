package io.cloudcauldron.bocan.scrobble

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.scrobble.net.ScrobbleHttp
import io.cloudcauldron.bocan.scrobble.providers.LastFmConfig
import io.cloudcauldron.bocan.scrobble.providers.LastFmProvider
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class LogRedactionTests {
    private val captured = mutableListOf<String>()
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            captured.add(message)
        }
    }

    private val secretKey = "SUPER_SECRET_SESSION_abc123"

    @Before
    fun plant() {
        captured.clear()
        Timber.plant(tree)
    }

    @After
    fun uproot() {
        Timber.uprootAll()
    }

    @Test
    fun `a credential never appears in provider logs`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(200).body("{\"session\":{\"name\":\"alice\",\"key\":\"$secretKey\"}}").build())
            server.enqueue(MockResponse.Builder().code(200).body("{\"scrobbles\":{}}").build())
            val dispatcher = UnconfinedTestDispatcher()
            val http = ScrobbleHttp(OkHttpClient(), CoroutineDispatchers(io = dispatcher, default = dispatcher))
            val store = FakeTokenStore()
            val provider = LastFmProvider(LastFmConfig("KEY", "SECRET", server.url("/2.0/")), store, http)

            provider.completeAuth("token")
            provider.scrobble(listOf(play()))

            assertTrue(captured.isNotEmpty(), "expected the provider to log")
            captured.forEach { line -> assertFalse(line.contains(secretKey), "session key leaked in: $line") }
        }
    }

    @Test
    fun `the log facade redacts sensitive keys`() {
        AppLog.forCategory(LogCategory.Scrobble).info("scrobble.test", mapOf("sessionKey" to secretKey, "apiKey" to "KEY_xyz"))
        val line = captured.single()
        assertTrue(line.contains("sessionKey=<redacted>"))
        assertFalse(line.contains(secretKey))
        assertFalse(line.contains("KEY_xyz"))
    }

    private fun play() = PlayEvent(
        trackId = 1,
        title = "Track",
        artist = "Artist",
        album = "Album",
        albumArtist = "Album Artist",
        durationSec = 200,
        playedAtEpochSec = 1_700_000_000,
        queueId = 1
    )
}
