package io.cloudcauldron.bocan.app.widget

import io.cloudcauldron.bocan.playback.queue.NowPlayingItem
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class WidgetStateTests {
    @Test
    fun `nothing playing maps to the empty state`() {
        assertEquals(WidgetState.EMPTY, WidgetState.fromPlayer(PlayerUiState()))
        assertFalse(WidgetState.fromPlayer(PlayerUiState()).hasContent)
    }

    @Test
    fun `a track maps its title, artist, and playing flag`() {
        val ui = PlayerUiState(
            current = NowPlayingItem("track:7", "Song", "Artist", "Album", "file:///art.jpg", 200_000),
            isPlaying = true
        )
        val state = WidgetState.fromPlayer(ui)
        assertTrue(state.hasContent)
        assertEquals("Song", state.title)
        assertEquals("Artist", state.subtitle)
        assertTrue(state.isPlaying)
        assertFalse(state.isPodcast)
        assertEquals("file:///art.jpg", state.artworkUri)
    }

    @Test
    fun `an episode is flagged as a podcast`() {
        val ui = PlayerUiState(current = NowPlayingItem("episode:abc", "Ep", null, null, null, 1_800_000))
        val state = WidgetState.fromPlayer(ui)
        assertTrue(state.isPodcast)
        assertEquals("", state.subtitle)
    }
}
