package io.cloudcauldron.bocan.app.widget

import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.cloudcauldron.bocan.playback.PlaybackService
import io.cloudcauldron.bocan.playback.session.SessionCommands
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Handles a widget control tap by connecting a short-lived [MediaController] to the
 * playback session and issuing the action, then releasing it. Routing through the session
 * (never the service internals) is the phase 10 contract, and using a controller respects
 * the foreground-start media exemption rather than starting the service directly.
 */
@UnstableApi
class WidgetControlCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[ACTION_KEY] ?: return
        val controller = connect(context) ?: return
        try {
            when (action) {
                ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                ACTION_NEXT -> controller.seekToNextMediaItem()
                ACTION_SKIP_FORWARD -> controller.sendCustomCommand(
                    SessionCommands.command(SessionCommands.SKIP_FORWARD),
                    android.os.Bundle.EMPTY
                )
            }
        } finally {
            controller.release()
        }
    }

    private suspend fun connect(context: Context): MediaController? {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        return suspendCancellableCoroutine { continuation ->
            future.addListener({
                continuation.resume(runCatching { future.get() }.getOrNull())
            }, MoreExecutors.directExecutor())
        }
    }

    companion object {
        val ACTION_KEY = ActionParameters.Key<String>("bocan.widget.action")
        const val ACTION_PLAY_PAUSE = "play_pause"
        const val ACTION_NEXT = "next"
        const val ACTION_SKIP_FORWARD = "skip_forward"
    }
}
