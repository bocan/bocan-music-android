package io.cloudcauldron.bocan.scrobble

/**
 * The classic scrobble eligibility rule, identical to the Mac's `ScrobbleRules` and to
 * the phone's own play-stats rule: a play counts when the track is at least 30 seconds
 * long and the listener reaches 50 percent of it or 4 minutes, whichever comes first.
 * Podcasts never scrobble.
 *
 * Kept pure so the rules table is unit tested directly. "Played" seconds exclude time
 * spent paused; the caller (the playback recorder) already accounts for that before an
 * event reaches the scrobbler.
 */
object ScrobbleRule {
    const val MIN_DURATION_SEC = 30
    const val PLAY_ABSOLUTE_SEC = 240
    const val PLAY_FRACTION = 0.5

    /** The played-seconds threshold at or beyond which a track of [durationSec] is eligible. */
    fun thresholdSec(durationSec: Int): Int = minOf((durationSec * PLAY_FRACTION).toInt(), PLAY_ABSOLUTE_SEC)

    /**
     * Whether a play is scrobble-eligible: never for a podcast, never under 30 seconds,
     * otherwise once played time reaches [thresholdSec].
     */
    fun isEligible(durationSec: Int, playedSec: Int, isPodcast: Boolean): Boolean =
        !isPodcast && durationSec >= MIN_DURATION_SEC && playedSec >= thresholdSec(durationSec)
}
