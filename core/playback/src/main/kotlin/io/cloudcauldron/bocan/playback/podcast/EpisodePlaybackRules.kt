package io.cloudcauldron.bocan.playback.podcast

import io.cloudcauldron.bocan.persistence.entities.EpisodeStateEntity
import io.cloudcauldron.bocan.persistence.model.PlayState

/**
 * The pure resume, completion, and speed rules for episodes, mirroring the Mac's
 * phase 21-5. Episodes have a single resume owner: these rules, never the music resume
 * path. Kept free of any player or Android type so the resume matrix is unit tested.
 */
object EpisodePlaybackRules {
    /** Do not resume within the first few seconds: the listener has effectively not started. */
    const val RESUME_FLOOR_MS = 5_000L

    /** Do not resume within the final window: near the end, restart from the top instead. */
    const val END_MARGIN_MS = 15_000L

    /**
     * The position to seek to when loading an episode. Resumes to the saved position only
     * when the episode is in progress and the position is between the floor and the end
     * margin; otherwise starts at 0 (unplayed, played, or too close to either end).
     */
    fun resumePosition(state: EpisodeStateEntity?, durationMs: Long): Long {
        if (state == null || state.playState != PlayState.InProgress) return 0
        val ceiling = durationMs - END_MARGIN_MS
        return if (state.playPositionMs in RESUME_FLOOR_MS..ceiling) state.playPositionMs else 0
    }

    /**
     * Whether the episode counts as complete at [positionMs], using the player's reported
     * [durationMs] (which may differ from the manifest's by a second or two).
     */
    fun isCompleted(positionMs: Long, durationMs: Long): Boolean = durationMs > 0 && positionMs >= durationMs - END_MARGIN_MS

    /**
     * Effective playback speed: the per-show override if the listener set one, else the
     * show's default from the manifest, else the app-wide podcast default. Music speed is
     * never resolved here.
     */
    fun effectiveSpeed(showOverride: Double?, showDefault: Double?, appDefault: Double): Double = showOverride ?: showDefault ?: appDefault
}
