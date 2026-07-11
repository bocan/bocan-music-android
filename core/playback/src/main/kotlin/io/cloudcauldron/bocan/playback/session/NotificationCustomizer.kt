package io.cloudcauldron.bocan.playback.session

import io.cloudcauldron.bocan.playback.MediaId

/** A transport action shown in the media notification, in display order. */
enum class NotificationAction {
    SkipBack,
    Previous,
    PlayPause,
    Next,
    SkipForward
}

/**
 * Chooses the notification's action set from the current item's type: music shows
 * previous, play/pause, next; a podcast episode swaps the seek arrows for skip-back and
 * skip-forward (the episode skip intervals). Three actions, which is exactly the compact
 * view's limit, so the compact and expanded views agree.
 *
 * Pure so the action selection is unit tested directly (phase 10 test plan); the provider
 * that renders these into platform actions with icons and session commands is service glue.
 */
object NotificationCustomizer {
    /** The ordered actions for [mediaId]; the music set for a track, null, or an unknown id. */
    fun actionsFor(mediaId: String?): List<NotificationAction> = if (isEpisode(mediaId)) {
        listOf(NotificationAction.SkipBack, NotificationAction.PlayPause, NotificationAction.SkipForward)
    } else {
        listOf(NotificationAction.Previous, NotificationAction.PlayPause, NotificationAction.Next)
    }

    /** The custom command an action maps to, or null for the built-in transport controls. */
    fun commandFor(action: NotificationAction): String? = when (action) {
        NotificationAction.SkipBack -> SessionCommands.SKIP_BACK
        NotificationAction.SkipForward -> SessionCommands.SKIP_FORWARD
        NotificationAction.Previous, NotificationAction.PlayPause, NotificationAction.Next -> null
    }

    private fun isEpisode(mediaId: String?): Boolean = mediaId?.let(MediaId::parse) is MediaId.Episode
}
