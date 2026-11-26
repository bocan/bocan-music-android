package io.cloudcauldron.bocan.playback.browse

import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.playback.MediaId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseTapTests {
    private fun queue(vararg trackIds: Long): List<MediaItem> =
        trackIds.map { MediaItem.Builder().setMediaId(MediaId.Track(it).raw).build() }

    @Test
    fun `an id round trips, with a parent that holds slashes`() {
        val tap = BrowseTap("bocan/playlist/12", trackId = 77, occurrence = 2)
        assertEquals(tap, BrowseTap.parse(tap.raw))
    }

    @Test
    fun `anything else is not a tap`() {
        listOf(
            "track:7",
            "episode:ep1",
            "bocan/album/3",
            "bocan/tap/",
            "bocan/tap/7",
            "bocan/tap/7/0",
            "bocan/tap/7/0/",
            "bocan/tap/x/0/bocan/album/3",
            "bocan/tap/7/x/bocan/album/3",
            "bocan/tap/7/-1/bocan/album/3"
        ).forEach { assertNull(BrowseTap.parse(it), it) }
    }

    @Test
    fun `the queue starts on the tapped copy of the song`() {
        val items = queue(1, 2, 1, 3)
        assertEquals(0, BrowseTap("p", 1, occurrence = 0).startIndexIn(items))
        assertEquals(2, BrowseTap("p", 1, occurrence = 1).startIndexIn(items))
        assertEquals(3, BrowseTap("p", 3).startIndexIn(items))
    }

    @Test
    fun `a copy that is gone falls back to the first copy`() {
        assertEquals(1, BrowseTap("p", 2, occurrence = 4).startIndexIn(queue(1, 2)))
    }

    @Test
    fun `a song that is not in the queue has no start`() {
        assertNull(BrowseTap("p", 9).startIndexIn(queue(1, 2)))
    }
}
