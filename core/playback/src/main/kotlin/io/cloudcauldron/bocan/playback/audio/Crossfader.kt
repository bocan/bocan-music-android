package io.cloudcauldron.bocan.playback.audio

import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The gain stage a fade drives: a 0..1 multiplier applied inside the effects chain. */
fun interface FadeSink {
    fun setFade(gain: Float)
}

/**
 * The honest half of "crossfade" on a single-player pipeline (phase 08 design note).
 * ExoPlayer cannot overlap two arbitrary tracks natively, so v1 does not pretend to:
 * the settings label is "Fade between tracks", not "Crossfade", and true overlapping
 * crossfade is deferred to a future second-player mix.
 *
 * What this does implement, both as volume ramps through the chain's [FadeSink] gain
 * stage (never `player.volume`, which the sleep timer owns):
 *
 *  - an end-of-track fade-out and next-track fade-in of [fadeSeconds] each, driven by
 *    playback position through [gainForPosition]; and
 *  - a short fade-out before a user-initiated skip ([fadeOutForManualSkip]).
 *
 * With [fadeSeconds] at 0 the position gain is always unity, so gapless playback is
 * untouched, which is the default.
 */
class Crossfader(private val sink: FadeSink, private val dispatchers: CoroutineDispatchers) {
    @Volatile
    var fadeSeconds: Int = 0

    /** Push the position-driven fade gain to the sink for the current [positionMs] within [durationMs]. */
    fun applyPositionFade(positionMs: Long, durationMs: Long) {
        sink.setFade(gainForPosition(positionMs, durationMs, fadeSeconds))
    }

    /**
     * Ramp the fade gain from unity to zero over [MANUAL_SKIP_FADE_MS] so a user skip
     * does not click; the caller performs the actual skip once this returns, then calls
     * [restore] to bring the next item up to full volume.
     */
    suspend fun fadeOutForManualSkip() = withContext(dispatchers.default) {
        var step = 1
        while (step <= MANUAL_SKIP_STEPS) {
            sink.setFade(1f - step.toFloat() / MANUAL_SKIP_STEPS)
            delay(MANUAL_SKIP_FADE_MS / MANUAL_SKIP_STEPS)
            step++
        }
    }

    /** Reset the fade gain to full volume (after a skip, or when fades are turned off). */
    fun restore() = sink.setFade(1f)

    companion object {
        const val MANUAL_SKIP_FADE_MS = 300L
        private const val MANUAL_SKIP_STEPS = 6
        private const val MS_PER_SECOND = 1000.0

        /**
         * The fade multiplier for [positionMs] within a track of [durationMs], given a
         * [fadeSeconds] window: a linear fade-in over the opening [fadeSeconds] and a
         * linear fade-out over the closing [fadeSeconds], unity in between. Returns unity
         * for a disabled ([fadeSeconds] 0) or unknown-length track, preserving gapless.
         */
        fun gainForPosition(positionMs: Long, durationMs: Long, fadeSeconds: Int): Float {
            if (fadeSeconds <= 0 || durationMs <= 0) return 1f
            val fadeMs = fadeSeconds * MS_PER_SECOND
            val position = positionMs.coerceIn(0, durationMs).toDouble()
            val fadeIn = if (position < fadeMs) position / fadeMs else 1.0
            val remaining = durationMs - position
            val fadeOut = if (remaining < fadeMs) remaining / fadeMs else 1.0
            return minOf(fadeIn, fadeOut).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
