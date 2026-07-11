package io.cloudcauldron.bocan.playback

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class MediaIdTests {
    @Test
    fun `track id formats and round trips`() {
        val id = MediaId.Track(42)
        assertEquals("track:42", id.raw)
        assertEquals(id, MediaId.parse(id.raw))
    }

    @Test
    fun `episode id formats and round trips`() {
        val id = MediaId.Episode("show-ep-1")
        assertEquals("episode:show-ep-1", id.raw)
        assertEquals(id, MediaId.parse(id.raw))
    }

    @Test
    fun `of entity uses the entity id`() {
        assertEquals(MediaId.Track(7), MediaId.of(track(id = 7)))
        assertEquals(MediaId.Episode("ep9"), MediaId.of(episode(id = "ep9")))
    }

    @Test
    fun `a non numeric track id does not parse`() {
        assertNull(MediaId.parse("track:notanumber"))
    }

    @Test
    fun `an empty episode id does not parse`() {
        assertNull(MediaId.parse("episode:"))
    }

    @Test
    fun `an unknown scheme does not parse`() {
        assertNull(MediaId.parse("playlist:1"))
        assertNull(MediaId.parse("42"))
    }
}
