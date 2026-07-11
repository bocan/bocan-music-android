package io.cloudcauldron.bocan.playback

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTests {
    private class FakeVolume(initial: Float) : PlayerVolume {
        var current = initial
        val history = mutableListOf<Float>()
        var paused = false
        override suspend fun getVolume(): Float = current
        override suspend fun setVolume(volume: Float) {
            current = volume
            history += volume
        }
        override suspend fun pause() {
            paused = true
        }
    }

    private fun dispatchers(scheduler: TestCoroutineScheduler): CoroutineDispatchers {
        val d = StandardTestDispatcher(scheduler)
        return CoroutineDispatchers(io = d, default = d, main = d)
    }

    @Test
    fun `a fixed timer counts down, fades to zero, pauses, and restores volume`() = runTest {
        val volume = FakeVolume(0.8f)
        val timer = SleepTimer(volume, emptyFlow(), dispatchers(testScheduler))
        timer.start(SleepDuration.Fixed(durationMs = 3_000))
        advanceUntilIdle()
        assertTrue(volume.paused, "should pause at expiry")
        assertTrue(volume.history.any { it == 0f }, "fade should reach zero")
        assertEquals(0.8f, volume.current, "original volume restored")
        assertEquals(SleepTimerState.Idle, timer.state.value)
        timer.dispose()
    }

    @Test
    fun `extend adds to a running countdown`() = runTest {
        val timer = SleepTimer(FakeVolume(1f), emptyFlow(), dispatchers(testScheduler))
        timer.start(SleepDuration.Fixed(durationMs = 5_000))
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(timer.state.value is SleepTimerState.Counting)
        timer.extend(minutes = 1)
        advanceTimeBy(4_000)
        runCurrent()
        // Without the extend the timer would have fired by now; it is still counting.
        assertTrue(timer.state.value is SleepTimerState.Counting)
        timer.dispose()
    }

    @Test
    fun `end of track mode fades on the next transition`() = runTest {
        val volume = FakeVolume(1f)
        val transitions = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val timer = SleepTimer(volume, transitions, dispatchers(testScheduler))
        timer.start(SleepDuration.EndOfTrack)
        runCurrent()
        assertEquals(SleepTimerState.WaitingForTrackEnd, timer.state.value)
        transitions.tryEmit(Unit)
        advanceUntilIdle()
        assertTrue(volume.paused)
        assertEquals(1f, volume.current)
        timer.dispose()
    }

    @Test
    fun `cancelling mid-fade restores the volume immediately`() = runTest {
        val volume = FakeVolume(0.8f)
        val timer = SleepTimer(volume, emptyFlow(), dispatchers(testScheduler))
        timer.start(SleepDuration.Fixed(durationMs = 1_000))
        advanceTimeBy(1_000)
        runCurrent()
        advanceTimeBy(5_000) // halfway through the ten second fade
        runCurrent()
        assertTrue(volume.current < 0.8f, "should be mid-fade")
        timer.cancel()
        runCurrent()
        assertEquals(0.8f, volume.current, "volume restored on cancel")
        assertEquals(SleepTimerState.Idle, timer.state.value)
        timer.dispose()
    }
}
