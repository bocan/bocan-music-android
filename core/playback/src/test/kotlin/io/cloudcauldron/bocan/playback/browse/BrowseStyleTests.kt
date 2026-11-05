package io.cloudcauldron.bocan.playback.browse

import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.playback.episode
import io.cloudcauldron.bocan.playback.track
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class BrowseStyleTests {
    private val style = BrowseStyle { hash -> hash?.let { Uri.parse("content://art/$it") } }

    @Test
    fun `a grid folder shows browsable children as a grid and playable ones as a list`() {
        val extras = checkNotNull(style.folder("bocan/albums/S", "S", null, grid = true).mediaMetadata.extras)
        assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM, extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE))
        assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM, extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE))
    }

    @Test
    fun `a list folder shows browsable children as a list`() {
        val extras = checkNotNull(style.folder("bocan/artists/S", "S", null, grid = false).mediaMetadata.extras)
        assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM, extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE))
    }

    @Test
    fun `a folder is browsable not playable and carries its artwork`() {
        val metadata = style.folder("bocan/album/3", "Souvlaki", "AA", grid = false).mediaMetadata
        assertEquals(true, metadata.isBrowsable)
        assertEquals(false, metadata.isPlayable)
        assertEquals(Uri.parse("content://art/AA"), metadata.artworkUri)
    }

    @Test
    fun `group titles appear only when given`() {
        val grouped = style.folder("bocan/artist/1", "Slowdive", null, grid = false, group = "Artists").mediaMetadata.extras
        assertEquals("Artists", grouped?.getString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE))
        val plain = style.folder("bocan/artist/1", "Slowdive", null, grid = false).mediaMetadata.extras
        assertFalse(checkNotNull(plain).containsKey(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE))

        val song = style.track(track(id = 9), group = "Songs").mediaMetadata.extras
        assertEquals("Songs", song?.getString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE))
        assertNull(style.track(track(id = 9)).mediaMetadata.extras)
    }

    @Test
    fun `tracks and episodes are playable with their own media type and id`() {
        val song = style.track(track(id = 9, title = "Alison"))
        assertEquals("track:9", song.mediaId)
        assertEquals("Alison", song.mediaMetadata.title)
        assertEquals(true, song.mediaMetadata.isPlayable)
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, song.mediaMetadata.mediaType)

        val show = style.episode(episode(id = "ep1"))
        assertEquals("episode:ep1", show.mediaId)
        assertEquals(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE, show.mediaMetadata.mediaType)
    }
}
