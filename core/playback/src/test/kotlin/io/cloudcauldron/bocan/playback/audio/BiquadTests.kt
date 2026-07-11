package io.cloudcauldron.bocan.playback.audio

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Signal tests for the pure biquad math: feed a steady sine through the filter and
 * measure the output-to-input RMS gain in dB. No Android or Media3 types, so this runs
 * as a plain JVM test.
 *
 * Tolerances: the peaking filter's gain at its centre frequency should hit the design
 * gain within 0.6 dB; two octaves away a one-octave bell has settled back to near unity,
 * checked within 1.5 dB. The generous bounds absorb RMS windowing leakage, not filter error.
 */
class BiquadTests {
    @Test
    fun `peaking filter delivers its design gain at the centre frequency`() {
        val filter = Biquad.peaking(SAMPLE_RATE, CENTER_HZ, gainDb = 6.0)
        val measured = gainDbAt(filter, CENTER_HZ)
        assertEquals(6.0, measured, CENTER_TOLERANCE_DB)
    }

    @Test
    fun `peaking filter is near unity two octaves above the centre`() {
        val filter = Biquad.peaking(SAMPLE_RATE, CENTER_HZ, gainDb = 6.0)
        val measured = gainDbAt(filter, CENTER_HZ * 4)
        assertEquals(0.0, measured, FAR_TOLERANCE_DB)
    }

    @Test
    fun `a cut lowers the centre frequency`() {
        val filter = Biquad.peaking(SAMPLE_RATE, CENTER_HZ, gainDb = -6.0)
        assertEquals(-6.0, gainDbAt(filter, CENTER_HZ), CENTER_TOLERANCE_DB)
    }

    @Test
    fun `a zero gain band is a pass-through`() {
        assertEquals(Biquad.PASSTHROUGH, Biquad.peaking(SAMPLE_RATE, CENTER_HZ, gainDb = 0.0))
    }

    @Test
    fun `low shelf boosts below the corner and leaves higher frequencies`() {
        val shelf = Biquad.lowShelf(SAMPLE_RATE, cornerHz = 80.0, gainDb = 6.0)
        assertEquals(6.0, gainDbAt(shelf, 40.0), SHELF_TOLERANCE_DB)
        assertTrue(gainDbAt(shelf, 2000.0) < 1.0, "the shelf should leave 2 kHz essentially untouched")
    }

    /** Steady-state RMS gain in dB of [filter] at [frequencyHz], after discarding the transient. */
    private fun gainDbAt(filter: Biquad, frequencyHz: Double): Double {
        val state = BiquadState()
        var sumIn = 0.0
        var sumOut = 0.0
        val step = 2.0 * PI * frequencyHz / SAMPLE_RATE
        for (n in 0 until WARMUP + WINDOW) {
            val x = AMPLITUDE * sin(step * n)
            val y = state.process(filter, x)
            if (n >= WARMUP) {
                sumIn += x * x
                sumOut += y * y
            }
        }
        val rmsIn = sqrt(sumIn / WINDOW)
        val rmsOut = sqrt(sumOut / WINDOW)
        return 20.0 * log10(rmsOut / rmsIn)
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CENTER_HZ = 1_000.0
        const val AMPLITUDE = 0.5
        const val WARMUP = 4_096
        const val WINDOW = 16_384
        const val CENTER_TOLERANCE_DB = 0.6
        const val FAR_TOLERANCE_DB = 1.5
        const val SHELF_TOLERANCE_DB = 1.0
    }
}
