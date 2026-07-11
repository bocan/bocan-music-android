package io.cloudcauldron.bocan.playback.audio

/**
 * A thin, testable seam over ExoPlayer's `skipSilenceEnabled`. The player property can
 * only be read and written on the main thread, so the effects wiring flips it through
 * this interface; a fake stands in for tests. Skip silence is great for spoken word and
 * off for music, so the default per context is decided by the caller (music off, podcasts
 * user-choice) rather than baked in here.
 */
fun interface SkipSilence {
    /** Enable or disable ExoPlayer's silence skipping. */
    fun setEnabled(enabled: Boolean)
}
