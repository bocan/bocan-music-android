package io.cloudcauldron.bocan.playback.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Immutable second-order IIR (biquad) filter coefficients, normalised so a0 = 1.
 * Pure math with no Android or Media3 type, so the whole EQ can be unit tested off
 * the audio thread. Coefficients come from Robert Bristow-Johnson's audio EQ cookbook
 * (the "RBJ cookbook"), the standard reference for peaking and shelving filters.
 *
 * Coefficients are computed once, off the audio thread, whenever a band gain or the
 * sample rate changes, then published to the processors via a volatile swap. The audio
 * thread only ever reads a [Biquad] and runs a [BiquadState] through it; it never does
 * trigonometry. See the phase 08 gotcha: never compute coefficients on the audio thread.
 */
class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
    companion object {
        /** The identity filter: output equals input, so a flat band costs one multiply. */
        val PASSTHROUGH = Biquad(1.0, 0.0, 0.0, 0.0, 0.0)

        /**
         * A peaking (bell) EQ filter centred at [centerHz] with [gainDb] of boost or cut
         * and a bandwidth of [bandwidthOctaves] octaves (1.0 gives roughly a one-octave
         * bell, near unity two octaves out). A gain of 0 dB returns [PASSTHROUGH] so a
         * flat band adds no filtering at all.
         */
        fun peaking(sampleRate: Int, centerHz: Double, gainDb: Double, bandwidthOctaves: Double = ONE_OCTAVE): Biquad {
            if (gainDb == 0.0) return PASSTHROUGH
            val a = sqrtGain(gainDb)
            val w0 = TWO_PI * centerHz / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            // RBJ bandwidth-to-alpha for a peaking filter: the sinh form ties alpha to a
            // bandwidth expressed in octaves, so the same value tracks across sample rates.
            val alpha = sinW0 * sinh(LN_2 / TWO * bandwidthOctaves * w0 / sinW0)

            val a0 = 1 + alpha / a
            return Biquad(
                b0 = (1 + alpha * a) / a0,
                b1 = (-TWO * cosW0) / a0,
                b2 = (1 - alpha * a) / a0,
                a1 = (-TWO * cosW0) / a0,
                a2 = (1 - alpha / a) / a0
            )
        }

        /**
         * A low-shelf filter that lifts (or cuts) everything below [cornerHz] by [gainDb],
         * used for the bass boost. [slope] is the shelf slope (1.0 is the steepest without
         * overshoot). A gain of 0 dB returns [PASSTHROUGH].
         */
        fun lowShelf(sampleRate: Int, cornerHz: Double, gainDb: Double, slope: Double = DEFAULT_SLOPE): Biquad {
            if (gainDb == 0.0) return PASSTHROUGH
            val a = sqrtGain(gainDb)
            val w0 = TWO_PI * cornerHz / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / TWO * sqrt((a + 1 / a) * (1 / slope - 1) + TWO)
            val twoSqrtAAlpha = TWO * sqrt(a) * alpha

            val a0 = (a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha
            return Biquad(
                b0 = a * ((a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha) / a0,
                b1 = TWO * a * ((a - 1) - (a + 1) * cosW0) / a0,
                b2 = a * ((a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha) / a0,
                a1 = -TWO * ((a - 1) + (a + 1) * cosW0) / a0,
                a2 = ((a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha) / a0
            )
        }

        /** RBJ's `A`: the square root of the linear gain, shared by peaking and shelf forms. */
        private fun sqrtGain(gainDb: Double): Double = Math.pow(GAIN_EXP_BASE, gainDb / GAIN_DB_DIVISOR)

        private const val TWO = 2.0
        private const val TWO_PI = TWO * PI
        private const val ONE_OCTAVE = 1.0
        private const val DEFAULT_SLOPE = 1.0
        private const val GAIN_EXP_BASE = 10.0

        // A = 10^(dB/40): the square root of the linear gain, since amplitude gain is 10^(dB/20).
        private const val GAIN_DB_DIVISOR = 40.0
        private val LN_2 = ln(TWO)
    }
}

/**
 * The per-channel delay memory for one [Biquad], evaluated with the transposed
 * direct form II (two state words, good numerical behaviour for audio). State is
 * double precision; input and output are the caller's concern. The audio thread owns
 * one [BiquadState] per band per channel and never shares it across threads, so no
 * synchronisation is needed here.
 */
class BiquadState {
    private var z1 = 0.0
    private var z2 = 0.0

    /** Zero the delay memory; call on a seek (onFlush) so stale samples do not smear across the cut. */
    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    /** Advance the filter one sample and return the output. */
    fun process(coeffs: Biquad, x: Double): Double {
        val y = coeffs.b0 * x + z1
        z1 = coeffs.b1 * x - coeffs.a1 * y + z2
        z2 = coeffs.b2 * x - coeffs.a2 * y
        return y
    }
}
