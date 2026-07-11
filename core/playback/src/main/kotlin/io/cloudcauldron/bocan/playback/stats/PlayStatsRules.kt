package io.cloudcauldron.bocan.playback.stats

import kotlin.math.min

/**
 * The play-versus-skip classification, mirroring the Mac app's rules, kept pure so
 * the rules table is unit tested directly.
 *
 * A play counts when the listener reaches 50 percent of the track or four minutes,
 * whichever comes first, and only when the track is at least 30 seconds long. A
 * shorter reach on an eligible track means the listener advanced early: a skip,
 * which records how many seconds in they got. Tracks under 30 seconds are neither
 * plays nor skips (interstitials, stingers).
 */
object PlayStatsRules {
    const val MIN_ELIGIBLE_DURATION_MS = 30_000L
    const val PLAY_ABSOLUTE_MS = 240_000L
    const val PLAY_FRACTION = 0.5

    /** The reached position at or beyond which the current track counts as played. */
    fun playThresholdMs(durationMs: Long): Long = min((durationMs * PLAY_FRACTION).toLong(), PLAY_ABSOLUTE_MS)

    /** Classify an item that is transitioning away, given how far into it the listener got. */
    fun classify(durationMs: Long, reachedMs: Long): PlayEvent {
        if (durationMs < MIN_ELIGIBLE_DURATION_MS) return PlayEvent.None
        val clampedReach = reachedMs.coerceIn(0, durationMs)
        return if (clampedReach >= playThresholdMs(durationMs)) {
            PlayEvent.Play
        } else {
            PlayEvent.Skip(afterSeconds = (clampedReach / MS_PER_SECOND).toInt())
        }
    }

    private const val MS_PER_SECOND = 1000L
}

/** The outcome of classifying one item play. */
sealed interface PlayEvent {
    /** Too short to matter, or in between: record nothing. */
    data object None : PlayEvent

    /** Count a play (and, for non-podcasts, emit a scrobble-eligible event). */
    data object Play : PlayEvent

    /** The listener advanced early; record the skip position. */
    data class Skip(val afterSeconds: Int) : PlayEvent
}
