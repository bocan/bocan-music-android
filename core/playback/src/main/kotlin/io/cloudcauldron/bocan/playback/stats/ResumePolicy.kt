package io.cloudcauldron.bocan.playback.stats

/**
 * Whether an item restores its last position on restart. Long items (audiobook-ish
 * tracks, or long clip sources) resume where the listener left off; ordinary music
 * restarts from the top. Podcast episodes replace this rule with their own in
 * phase 07. Kept pure so the boundary is unit tested.
 */
object ResumePolicy {
    const val RESUME_THRESHOLD_MS = 20L * 60L * 1000L

    /** True if an item this long should restore its saved position rather than restart. */
    fun shouldResume(durationMs: Long): Boolean = durationMs > RESUME_THRESHOLD_MS
}
