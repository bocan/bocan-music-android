package io.cloudcauldron.bocan.scrobble

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.scrobble.providers.ProviderId
import io.cloudcauldron.bocan.scrobble.queue.ScrobbleQueue
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ScrobbleServiceTests {
    private val track = ScrobbleTrack("Song", "Artist", "Album", "Album Artist", durationSec = 200)

    private fun runServiceTest(
        enabled: Set<String> = setOf(ProviderId.LAST_FM),
        metadata: suspend (Long) -> ScrobbleTrack? = { track },
        block: suspend TestScope.(ScrobbleService, FakeProvider) -> Unit
    ) = runTest {
        val db = BocanDatabase.createInMemory(ApplicationProvider.getApplicationContext(), UnconfinedTestDispatcher(testScheduler))
        try {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val dispatchers = CoroutineDispatchers(io = dispatcher, default = dispatcher)
            val provider = FakeProvider(ProviderId.LAST_FM)
            val queue = ScrobbleQueue(db.scrobbleDao(), dispatchers)
            val service = ScrobbleService(listOf(provider), queue, metadata, { enabled }, CoroutineScope(dispatcher), dispatchers)
            block(service, provider)
        } finally {
            db.close()
        }
    }

    @Test
    fun `an eligible play is enqueued and drained to the provider`() = runServiceTest { service, provider ->
        service.onPlayEligible(trackId = 42, playedAt = Instant.parse("2026-07-11T00:00:00Z"), isPodcast = false)
        advanceUntilIdle()
        assertEquals(listOf(42L), provider.scrobbled.map { it.trackId })
        assertEquals("Song", provider.scrobbled.single().title)
    }

    @Test
    fun `a podcast never scrobbles`() = runServiceTest { service, provider ->
        service.onPlayEligible(trackId = 42, playedAt = Instant.now(), isPodcast = true)
        advanceUntilIdle()
        assertTrue(provider.scrobbled.isEmpty())
    }

    @Test
    fun `no enabled providers means nothing is submitted`() = runServiceTest(enabled = emptySet()) { service, provider ->
        service.onPlayEligible(trackId = 42, playedAt = Instant.now(), isPodcast = false)
        advanceUntilIdle()
        assertTrue(provider.scrobbled.isEmpty())
    }

    @Test
    fun `unresolved metadata submits nothing`() = runServiceTest(metadata = { null }) { service, provider ->
        service.onPlayEligible(trackId = 42, playedAt = Instant.now(), isPodcast = false)
        advanceUntilIdle()
        assertTrue(provider.scrobbled.isEmpty())
    }

    @Test
    fun `now playing is sent for a track and skipped for a podcast`() = runServiceTest { service, provider ->
        service.onNowPlaying(trackId = 42, isPodcast = false)
        advanceUntilIdle()
        assertEquals(1, provider.nowPlaying.size)

        service.onNowPlaying(trackId = 43, isPodcast = true)
        advanceUntilIdle()
        assertEquals(1, provider.nowPlaying.size)
    }
}
