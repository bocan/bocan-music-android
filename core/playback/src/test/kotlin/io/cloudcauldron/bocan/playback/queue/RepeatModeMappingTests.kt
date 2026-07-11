package io.cloudcauldron.bocan.playback.queue

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlin.test.assertEquals
import org.junit.Test

@UnstableApi
class RepeatModeMappingTests {
    @Test
    fun `each repeat mode maps to its player constant`() {
        assertEquals(Player.REPEAT_MODE_OFF, RepeatMode.Off.toPlayer())
        assertEquals(Player.REPEAT_MODE_ONE, RepeatMode.One.toPlayer())
        assertEquals(Player.REPEAT_MODE_ALL, RepeatMode.All.toPlayer())
    }

    @Test
    fun `player constants map back to repeat modes`() {
        assertEquals(RepeatMode.Off, RepeatMode.fromPlayer(Player.REPEAT_MODE_OFF))
        assertEquals(RepeatMode.One, RepeatMode.fromPlayer(Player.REPEAT_MODE_ONE))
        assertEquals(RepeatMode.All, RepeatMode.fromPlayer(Player.REPEAT_MODE_ALL))
    }

    @Test
    fun `an unknown player constant falls back to off`() {
        assertEquals(RepeatMode.Off, RepeatMode.fromPlayer(-1))
    }
}
