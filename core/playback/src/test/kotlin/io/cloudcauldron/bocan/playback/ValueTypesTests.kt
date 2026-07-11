package io.cloudcauldron.bocan.playback

import io.cloudcauldron.bocan.playback.queue.NowPlayingItem
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import io.cloudcauldron.bocan.playback.queue.QueueSnapshot
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import io.cloudcauldron.bocan.playback.stats.ScrobbleEvent
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** Exercises the plain value types (errors, events, UI state) so their fields and defaults are covered. */
class ValueTypesTests {
    @Test
    fun `playback errors carry their context`() {
        assertEquals("cause", PlaybackError.PlayerInitFailed(RuntimeException("cause")).reason.message)
        assertEquals("track:9", PlaybackError.UnknownMediaId("track:9").mediaId)
        assertEquals("track:9", PlaybackError.ItemUnplayable("track:9").mediaId)
        assertTrue(PlaybackError.MediaUnavailable.message!!.isNotEmpty())
        assertTrue(PlaybackError.QueuePersistenceFailed(RuntimeException()).message!!.isNotEmpty())
    }

    @Test
    fun `a scrobble event distinguishes tracks from podcasts`() {
        val now = Instant.EPOCH
        val trackEvent = ScrobbleEvent(mediaId = "track:1", trackId = 1, playedAt = now, isPodcast = false)
        val podcastEvent = ScrobbleEvent(mediaId = "episode:x", trackId = null, playedAt = now, isPodcast = true)
        assertEquals(1L, trackEvent.trackId)
        assertNull(podcastEvent.trackId)
        assertTrue(podcastEvent.isPodcast)
    }

    @Test
    fun `the default ui state is an empty stopped queue`() {
        val state = PlayerUiState()
        assertNull(state.current)
        assertTrue(state.queue.isEmpty())
        assertEquals(-1, state.queueIndex)
        assertEquals(RepeatMode.Off, state.repeatMode)
        assertEquals(1.0f, state.speed)
    }

    @Test
    fun `a now playing item and populated state hold their fields`() {
        val item = NowPlayingItem("track:1", "Title", "Artist", "Album", null, 1000)
        val state = PlayerUiState(current = item, queue = listOf(item), queueIndex = 0, isPlaying = true)
        assertEquals("Title", state.current?.title)
        assertEquals(1, state.queue.size)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `the empty snapshot has no items`() {
        assertTrue(QueueSnapshot.EMPTY.mediaIds.isEmpty())
        assertEquals(-1, QueueSnapshot.EMPTY.index)
    }
}
