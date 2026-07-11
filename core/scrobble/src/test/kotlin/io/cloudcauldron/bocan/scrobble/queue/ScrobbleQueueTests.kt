package io.cloudcauldron.bocan.scrobble.queue

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.persistence.daos.ScrobbleDao
import io.cloudcauldron.bocan.scrobble.CoroutineDispatchers
import io.cloudcauldron.bocan.scrobble.FakeProvider
import io.cloudcauldron.bocan.scrobble.PlayEvent
import io.cloudcauldron.bocan.scrobble.SubmissionOutcome
import io.cloudcauldron.bocan.scrobble.providers.ScrobbleProvider
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ScrobbleQueueTests {
    private val providerId = "lastfm"

    private fun runQueueTest(block: suspend TestScope.(ScrobbleDao, MutableClock) -> Unit) = runTest {
        val db = BocanDatabase.createInMemory(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
        try {
            block(db.scrobbleDao(), MutableClock())
        } finally {
            db.close()
        }
    }

    private fun queue(dao: ScrobbleDao, clock: MutableClock, scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) = ScrobbleQueue(
        dao,
        CoroutineDispatchers(io = StandardTestDispatcher(scheduler), default = StandardTestDispatcher(scheduler)),
        clock = clock::now
    )

    @Test
    fun `offline enqueue then drain submits in order exactly once`() = runQueueTest { dao, clock ->
        val queue = queue(dao, clock, testScheduler)
        val provider = FakeProvider(providerId)
        (1L..3L).forEach { queue.enqueue(providerId, play(it)) }

        queue.drain(mapOf(providerId to provider), setOf(providerId))
        assertEquals(listOf(1L, 2L, 3L), provider.scrobbled.map { it.trackId })
        assertEquals(0, dao.observeQueueSize().first())

        // Draining again submits nothing: the rows were deleted on success.
        queue.drain(mapOf(providerId to provider), setOf(providerId))
        assertEquals(3, provider.scrobbled.size)
    }

    @Test
    fun `a retryable failure backs off and is not dead-lettered`() = runQueueTest { dao, clock ->
        val queue = queue(dao, clock, testScheduler)
        val provider = FakeProvider(providerId) { SubmissionOutcome.Retry("http 503") }
        queue.enqueue(providerId, play(1))

        queue.drain(mapOf(providerId to provider), setOf(providerId))
        val row = dao.activeForProvider(providerId).single()
        assertEquals(1, row.attempts)
        assertEquals(clock.now().plusSeconds(1), row.nextAttemptAt)
        assertTrue(!row.deadLettered)

        // Still backed off: due() at the same instant returns nothing.
        assertTrue(dao.due(clock.now(), 10).isEmpty())
    }

    @Test
    fun `a permanent failure dead-letters immediately`() = runQueueTest { dao, clock ->
        val queue = queue(dao, clock, testScheduler)
        val provider = FakeProvider(providerId) { SubmissionOutcome.PermanentFailure("http 400") }
        queue.enqueue(providerId, play(1))

        queue.drain(mapOf(providerId to provider), setOf(providerId))
        assertEquals(1, queue.observeDeadLettered().first().size)
        assertEquals(0, dao.observeQueueSize().first())
    }

    @Test
    fun `retries exhaust into the dead-letter after ten attempts`() = runQueueTest { dao, clock ->
        val queue = queue(dao, clock, testScheduler)
        val provider = FakeProvider(providerId) { SubmissionOutcome.Retry("http 503") }
        queue.enqueue(providerId, play(1))

        repeat(RetryPolicy.MAX_ATTEMPTS) {
            clock.advanceSeconds(RetryPolicy.MAX_DELAY_SEC) // jump past any backoff so the row is due
            queue.drain(mapOf(providerId to provider), setOf(providerId))
        }
        assertEquals(1, queue.observeDeadLettered().first().size)
    }

    @Test
    fun `auth-expired leaves the row queued`() = runQueueTest { dao, clock ->
        val queue = queue(dao, clock, testScheduler)
        val provider = FakeProvider(providerId) { SubmissionOutcome.AuthExpired }
        queue.enqueue(providerId, play(1))

        queue.drain(mapOf(providerId to provider), setOf(providerId))
        val row = dao.activeForProvider(providerId).single()
        assertEquals(0, row.attempts)
        assertTrue(!row.deadLettered)
    }

    @Test
    fun `an unauthenticated provider is paused and its rows wait`() = runQueueTest { dao, clock ->
        val queue = queue(dao, clock, testScheduler)
        val provider = FakeProvider(providerId, authenticated = false)
        queue.enqueue(providerId, play(1))

        queue.drain(mapOf<String, ScrobbleProvider>(providerId to provider), setOf(providerId))
        assertEquals(0, provider.scrobbleCalls)
        assertEquals(1, dao.observeQueueSize().first())
    }

    @Test
    fun `a duplicate within the same minute enqueues once`() = runQueueTest { dao, _ ->
        val queue = queue(dao, MutableClock(), testScheduler)
        val first = queue.enqueue(providerId, play(trackId = 7, epochSec = 100)) // minute 1 (60..119)
        val second = queue.enqueue(providerId, play(trackId = 7, epochSec = 119)) // same minute 1
        assertTrue(first != null)
        assertNull(second)
        assertEquals(1, dao.activeForProvider(providerId).size)
    }

    private fun play(trackId: Long, epochSec: Long = trackId * 1000) = PlayEvent(
        trackId = trackId,
        title = "Track $trackId",
        artist = "Artist",
        album = "Album",
        albumArtist = "Album Artist",
        durationSec = 200,
        playedAtEpochSec = epochSec
    )

    private class MutableClock(start: Instant = Instant.parse("2026-07-11T00:00:00Z")) {
        private var current = start
        fun now(): Instant = current
        fun advanceSeconds(seconds: Long) {
            current = current.plusSeconds(seconds)
        }
    }
}
