package io.cloudcauldron.bocan.playback.audio

import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ReplayGainMathTests {
    @Test
    fun `off mode is unity`() {
        val values = ReplayGainValues(trackGainDb = -6.0, trackPeak = 0.9, albumGainDb = -4.0, albumPeak = 0.95)
        assertEquals(ReplayGainMath.UNITY, ReplayGainMath.factor(ReplayGainMode.Off, values), TOLERANCE)
    }

    @Test
    fun `track mode computes ten to the gain over twenty`() {
        val values = ReplayGainValues(trackGainDb = -6.0, trackPeak = null, albumGainDb = null, albumPeak = null)
        val expected = 10.0.pow(-6.0 / 20.0)
        assertEquals(expected, ReplayGainMath.factor(ReplayGainMode.Track, values), TOLERANCE)
    }

    @Test
    fun `preamp shifts the gain`() {
        val values = ReplayGainValues(trackGainDb = -6.0, trackPeak = null, albumGainDb = null, albumPeak = null)
        val expected = 10.0.pow((-6.0 + 3.0) / 20.0)
        assertEquals(expected, ReplayGainMath.factor(ReplayGainMode.Track, values, preampDb = 3.0), TOLERANCE)
    }

    @Test
    fun `peak clamp prevents clipping on a positive gain`() {
        val values = ReplayGainValues(trackGainDb = 6.0, trackPeak = 0.9, albumGainDb = null, albumPeak = null)
        val factor = ReplayGainMath.factor(ReplayGainMode.Track, values)
        // Raw factor 10^(6/20) ~= 1.995 would push a 0.9 peak to ~1.8: clamp to 1/0.9.
        assertEquals(1.0 / 0.9, factor, TOLERANCE)
        assertTrue(0.9 * factor <= 1.0 + TOLERANCE)
    }

    @Test
    fun `album mode falls back to track values when album is absent`() {
        val values = ReplayGainValues(trackGainDb = -8.0, trackPeak = 0.8, albumGainDb = null, albumPeak = null)
        val expected = 10.0.pow(-8.0 / 20.0)
        assertEquals(expected, ReplayGainMath.factor(ReplayGainMode.Album, values), TOLERANCE)
    }

    @Test
    fun `album mode prefers album values`() {
        val values = ReplayGainValues(trackGainDb = -8.0, trackPeak = 0.8, albumGainDb = -4.0, albumPeak = 0.95)
        val expected = 10.0.pow(-4.0 / 20.0)
        assertEquals(expected, ReplayGainMath.factor(ReplayGainMode.Album, values), TOLERANCE)
    }

    @Test
    fun `a missing gain leaves the item untouched`() {
        val values = ReplayGainValues(trackGainDb = null, trackPeak = 0.9, albumGainDb = null, albumPeak = null)
        assertEquals(ReplayGainMath.UNITY, ReplayGainMath.factor(ReplayGainMode.Track, values), TOLERANCE)
    }

    @Test
    fun `clamp leaves a safe factor untouched and ignores non positive peaks`() {
        assertEquals(2.0, ReplayGainMath.clampToPeak(2.0, 0.5), TOLERANCE)
        assertEquals(2.0, ReplayGainMath.clampToPeak(3.0, 0.5), TOLERANCE)
        assertEquals(2.0, ReplayGainMath.clampToPeak(2.0, null), TOLERANCE)
        assertEquals(2.0, ReplayGainMath.clampToPeak(2.0, 0.0), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
