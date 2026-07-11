package io.cloudcauldron.bocan.playback.session

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class NotificationCustomizerTests {
    @Test
    fun `a track shows previous, play-pause, next`() {
        assertEquals(
            listOf(NotificationAction.Previous, NotificationAction.PlayPause, NotificationAction.Next),
            NotificationCustomizer.actionsFor("track:42")
        )
    }

    @Test
    fun `an episode swaps in skip-back and skip-forward`() {
        assertEquals(
            listOf(NotificationAction.SkipBack, NotificationAction.PlayPause, NotificationAction.SkipForward),
            NotificationCustomizer.actionsFor("episode:abc")
        )
    }

    @Test
    fun `an unknown or null id falls back to the music set`() {
        assertEquals(NotificationCustomizer.actionsFor("track:1"), NotificationCustomizer.actionsFor(null))
        assertEquals(NotificationCustomizer.actionsFor("track:1"), NotificationCustomizer.actionsFor("garbage"))
    }

    @Test
    fun `only the skip actions carry a custom command`() {
        assertEquals(SessionCommands.SKIP_BACK, NotificationCustomizer.commandFor(NotificationAction.SkipBack))
        assertEquals(SessionCommands.SKIP_FORWARD, NotificationCustomizer.commandFor(NotificationAction.SkipForward))
        assertNull(NotificationCustomizer.commandFor(NotificationAction.PlayPause))
        assertNull(NotificationCustomizer.commandFor(NotificationAction.Previous))
        assertNull(NotificationCustomizer.commandFor(NotificationAction.Next))
    }

    @Test
    fun `every notification action set has three entries for the compact view`() {
        assertEquals(3, NotificationCustomizer.actionsFor("track:1").size)
        assertEquals(3, NotificationCustomizer.actionsFor("episode:x").size)
    }
}
