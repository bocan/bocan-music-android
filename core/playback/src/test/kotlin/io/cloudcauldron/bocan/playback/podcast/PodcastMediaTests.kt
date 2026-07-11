package io.cloudcauldron.bocan.playback.podcast

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PodcastMediaTests {
    @Test
    fun `episode media ids are podcasts and are excluded from scrobbling`() {
        assertTrue(isPodcastMedia("episode:show-ep-1"))
    }

    @Test
    fun `track media ids are not podcasts`() {
        assertFalse(isPodcastMedia("track:42"))
    }

    @Test
    fun `an unparseable media id is not a podcast`() {
        assertFalse(isPodcastMedia("garbage"))
    }
}
