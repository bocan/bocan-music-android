package io.cloudcauldron.bocan.playback.session

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand

/**
 * The custom session commands every external surface (notification, Auto, widget) uses to
 * drive playback. Nothing binds to the service internals: a surface sends one of these
 * namespaced commands and the session's onCustomCommand applies it (phase 10 contract).
 * Action strings are stable and namespaced `io.cloudcauldron.bocan.command.<name>`. The
 * plain action constants are opt-in free; only the [SessionCommand] builders touch the
 * unstable Media3 type.
 */
object SessionCommands {
    const val NAMESPACE = "io.cloudcauldron.bocan.command."
    const val SKIP_BACK = NAMESPACE + "skip_back"
    const val SKIP_FORWARD = NAMESPACE + "skip_forward"
    const val CYCLE_SPEED = NAMESPACE + "cycle_speed"
    const val TOGGLE_SHUFFLE = NAMESPACE + "toggle_shuffle"

    /** Every custom action, for advertising in the connection result and building buttons. */
    val ALL: List<String> = listOf(SKIP_BACK, SKIP_FORWARD, CYCLE_SPEED, TOGGLE_SHUFFLE)

    /** The [SessionCommand] for [action]. */
    @UnstableApi
    fun command(action: String): SessionCommand = SessionCommand(action, Bundle.EMPTY)

    /** Every custom command, for the session's available-commands set. */
    @UnstableApi
    fun all(): List<SessionCommand> = ALL.map(::command)
}
