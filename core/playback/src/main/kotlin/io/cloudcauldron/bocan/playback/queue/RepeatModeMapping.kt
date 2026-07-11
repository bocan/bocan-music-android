package io.cloudcauldron.bocan.playback.queue

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Map [RepeatMode] to Media3's Player repeat constant at the player boundary. */
@UnstableApi
internal fun RepeatMode.toPlayer(): Int = when (this) {
    RepeatMode.Off -> Player.REPEAT_MODE_OFF
    RepeatMode.One -> Player.REPEAT_MODE_ONE
    RepeatMode.All -> Player.REPEAT_MODE_ALL
}

/** Map a Media3 Player repeat constant back to a [RepeatMode]. */
@UnstableApi
internal fun RepeatMode.Companion.fromPlayer(mode: Int): RepeatMode = when (mode) {
    Player.REPEAT_MODE_ONE -> RepeatMode.One
    Player.REPEAT_MODE_ALL -> RepeatMode.All
    else -> RepeatMode.Off
}
