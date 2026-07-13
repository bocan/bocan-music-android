package io.cloudcauldron.bocan.playback

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.playback.audio.ReplayGainValues
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MediaItemFactoryTests {
    private val factory = MediaItemFactory(FakeMediaFileResolver())

    @Test
    fun `a track maps id, uri, and metadata`() {
        val item = factory.forTrack(track(id = 1, title = "Song", artistName = "Band", albumName = "Record"))
        assertEquals("track:1", item.mediaId)
        assertEquals("file:///media/library/Artist/Album/1.flac", item.localConfiguration?.uri.toString())
        assertEquals("Song", item.mediaMetadata.title)
        assertEquals("Band", item.mediaMetadata.artist)
        assertEquals("Record", item.mediaMetadata.albumTitle)
        assertEquals(1, item.mediaMetadata.trackNumber)
        assertEquals(1, item.mediaMetadata.discNumber)
    }

    @Test
    fun `a track carries its replaygain values as the tag`() {
        val item = factory.forTrack(track(rgTrackGain = -6.0, rgTrackPeak = 0.9, rgAlbumGain = -4.0, rgAlbumPeak = 0.95))
        val tag = item.localConfiguration?.tag as? ReplayGainValues
        assertEquals(ReplayGainValues(-6.0, 0.9, -4.0, 0.95), tag)
    }

    @Test
    fun `a clip track windows the shared source file and keeps its own id`() {
        val clip = track(id = 5, clipSourceTrackId = 1, clipStartMs = 10_000, clipEndMs = 20_000, relPath = "Artist/Album/source.flac")
        val item = factory.forTrack(clip)
        assertEquals("track:5", item.mediaId)
        assertEquals("file:///media/library/Artist/Album/source.flac", item.localConfiguration?.uri.toString())
        assertEquals(10_000, item.clippingConfiguration.startPositionMs)
        assertEquals(20_000, item.clippingConfiguration.endPositionMs)
    }

    @Test
    fun `a non clip track has no clipping window`() {
        val item = factory.forTrack(track(clipStartMs = null, clipEndMs = null))
        assertEquals(0, item.clippingConfiguration.startPositionMs)
    }

    @Test
    fun `an absent artwork hash yields no artwork uri`() {
        val item = MediaItemFactory(FakeMediaFileResolver(artworkPresent = false)).forTrack(track(artworkHash = "x"))
        assertNull(item.mediaMetadata.artworkUri)
    }

    @Test
    fun `an episode maps to an episode id with no replaygain`() {
        val item = factory.forEpisode(episode(id = "ep7", title = "Talk"), showArtworkHash = null)
        assertEquals("episode:ep7", item.mediaId)
        assertEquals("Talk", item.mediaMetadata.title)
        assertEquals(ReplayGainValues.NONE, item.localConfiguration?.tag)
    }

    @Test
    fun `an episode carries its show's artwork uri`() {
        val item = factory.forEpisode(episode(id = "ep7"), showArtworkHash = "showcover")
        assertEquals("file:///media/artwork/showcover", item.mediaMetadata.artworkUri?.toString())
    }

    @Test
    fun `an episode with no show artwork has no artwork uri`() {
        val item = factory.forEpisode(episode(id = "ep7"), showArtworkHash = null)
        assertNull(item.mediaMetadata.artworkUri)
    }

    @Test
    fun `now playing reads display fields back off the item`() {
        val item = factory.forTrack(track(id = 3, title = "Round", artistName = "Trip"))
        val nowPlaying = MediaItemFactory.toNowPlaying(item)
        assertEquals("track:3", nowPlaying.mediaId)
        assertEquals("Round", nowPlaying.title)
        assertEquals("Trip", nowPlaying.artist)
        assertTrue(nowPlaying.durationMs > 0)
    }
}
