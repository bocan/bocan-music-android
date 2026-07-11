package io.cloudcauldron.bocan.playback.queue

/**
 * Queue repeat behaviour, mirroring the Mac app. Mapped to Media3's
 * `Player.REPEAT_MODE_*` ints at the player boundary in QueueController.
 */
enum class RepeatMode {
    /** Stop at the end of the queue. */
    Off,

    /** Repeat the current item forever. */
    One,

    /** Loop back to the start of the queue after the last item. */
    All
    ;

    /** Anchors the Player-int mapping extensions defined at the player boundary. */
    companion object
}
