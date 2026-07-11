package io.cloudcauldron.bocan.playback.audio

import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CrossfaderTests {
    @Test
    fun `disabled fade leaves the gain at unity, preserving gapless`() {
        assertEquals(1f, Crossfader.gainForPosition(positionMs = 0, durationMs = 200_000, fadeSeconds = 0))
        assertEquals(1f, Crossfader.gainForPosition(positionMs = 199_000, durationMs = 200_000, fadeSeconds = 0))
    }

    @Test
    fun `fade-out ramps to zero over the closing window`() {
        val duration = 200_000L
        val fade = 5
        // Full volume in the middle.
        assertEquals(1f, Crossfader.gainForPosition(100_000, duration, fade))
        // Half way through the closing window: about half volume.
        assertEquals(0.5f, Crossfader.gainForPosition(duration - 2_500, duration, fade), TOLERANCE)
        // At the transition: zero.
        assertEquals(0f, Crossfader.gainForPosition(duration, duration, fade), TOLERANCE)
    }

    @Test
    fun `fade-in ramps up from the start of the next track`() {
        val duration = 200_000L
        val fade = 4
        assertEquals(0f, Crossfader.gainForPosition(0, duration, fade), TOLERANCE)
        assertEquals(0.5f, Crossfader.gainForPosition(2_000, duration, fade), TOLERANCE)
        assertEquals(1f, Crossfader.gainForPosition(4_000, duration, fade), TOLERANCE)
    }

    @Test
    fun `unknown duration keeps unity`() {
        assertEquals(1f, Crossfader.gainForPosition(1_000, durationMs = 0, fadeSeconds = 5))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a manual skip fades out to zero then restore returns to full`() = runTest {
        val gains = mutableListOf<Float>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val crossfader = Crossfader({ gains.add(it) }, CoroutineDispatchers(io = dispatcher, default = dispatcher, main = dispatcher))

        crossfader.fadeOutForManualSkip()
        runCurrent()

        assertTrue(gains.isNotEmpty(), "the skip fade should emit ramp steps")
        assertEquals(0f, gains.last(), TOLERANCE)
        assertTrue(gains.zipWithNext().all { (a, b) -> b <= a }, "the ramp should be monotonically decreasing")

        crossfader.restore()
        assertEquals(1f, gains.last())
    }

    private companion object {
        const val TOLERANCE = 0.02f
    }
}
