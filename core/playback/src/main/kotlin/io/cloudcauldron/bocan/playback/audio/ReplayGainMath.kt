package io.cloudcauldron.bocan.playback.audio

import kotlin.math.pow

/**
 * The pure ReplayGain gain-factor computation, kept off the audio thread's hot
 * path and free of any Android or Media3 type so it can be unit tested directly.
 *
 * The linear factor is `10^((gain + preamp) / 20)`. To prevent the boosted signal
 * from clipping, the factor is clamped so `peak * factor <= 1.0` whenever a peak
 * is known: without that clamp a positive gain on an already-hot master would wrap
 * samples and distort.
 */
object ReplayGainMath {
    /** Unity gain: the signal passes through unchanged. */
    const val UNITY = 1.0

    /**
     * The linear multiplier for [mode] given an item's [values] and a [preampDb]
     * offset. Returns [UNITY] for [ReplayGainMode.Off], or when the selected mode's
     * gain is absent (an un-analysed item plays untouched rather than silent).
     */
    fun factor(mode: ReplayGainMode, values: ReplayGainValues, preampDb: Double = 0.0): Double {
        val gainDb = selectGain(mode, values) ?: return UNITY
        val raw = 10.0.pow((gainDb + preampDb) / DB_DIVISOR)
        return clampToPeak(raw, selectPeak(mode, values))
    }

    /** The gain in dB for [mode], or null when there is nothing to apply (Off, or an un-analysed item). */
    private fun selectGain(mode: ReplayGainMode, values: ReplayGainValues): Double? = when (mode) {
        ReplayGainMode.Off -> null
        ReplayGainMode.Track -> values.trackGainDb
        ReplayGainMode.Album -> values.albumGainDb ?: values.trackGainDb
    }

    /** The peak for [mode] used to clamp the factor, or null when unknown. */
    private fun selectPeak(mode: ReplayGainMode, values: ReplayGainValues): Double? = when (mode) {
        ReplayGainMode.Off -> null
        ReplayGainMode.Track -> values.trackPeak
        ReplayGainMode.Album -> values.albumPeak ?: values.trackPeak
    }

    /**
     * Clamp [factor] so a known [peak] cannot be pushed above full scale. A null or
     * non-positive peak leaves the factor unclamped (nothing to protect against).
     */
    fun clampToPeak(factor: Double, peak: Double?): Double {
        if (peak == null || peak <= 0.0) return factor
        val ceiling = 1.0 / peak
        return if (factor > ceiling) ceiling else factor
    }

    private const val DB_DIVISOR = 20.0
}
