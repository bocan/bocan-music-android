package io.cloudcauldron.bocan.playback.browse

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaId
import io.cloudcauldron.bocan.playback.episode
import io.cloudcauldron.bocan.playback.track
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
@RunWith(AndroidJUnit4::class)
class BrowseSearchTests {
    private val souvlakiSpaceStation = track(id = 1, title = "Souvlaki Space Station", artistName = "Slowdive", albumName = "Souvlaki")
    private val alison = track(id = 2, title = "Alison", artistName = "Slowdive", albumName = "Souvlaki")
    private val slow = track(id = 3, title = "Slow", artistName = "Low", albumName = "Things We Lost")
    private val notDownloaded = track(id = 4, title = "Slowness", artistName = "Someone", albumName = "Else")
        .copy(downloadState = DownloadState.Pending)

    private val search = FakeSearchDao(
        tracks = listOf(souvlakiSpaceStation, alison, notDownloaded, slow),
        albums = listOf(
            AlbumEntity(101, "Souvlaki", "Slowdive", 1993, "S2", trackCount = 10),
            AlbumEntity(100, "Slowdive", "Slowdive", 1991, "S1", trackCount = 3)
        ),
        artists = listOf(ArtistEntity(10, "Slowdive Tribute"), ArtistEntity(11, "Slowdive"), ArtistEntity(12, "Mogwai")),
        shows = listOf(PodcastEntity(5, "The Slow Show", null, null, "P5", null), PodcastEntity(6, "Fast Talk", null, null, null, null))
    )

    private val browse = FakeBrowseDao(
        songs = listOf(souvlakiSpaceStation, alison, slow),
        artistTracks = mapOf(11L to listOf(souvlakiSpaceStation, alison)),
        albumTracks = mapOf(101L to listOf(alison, souvlakiSpaceStation)),
        episodesByShow = mapOf(6L to listOf(episode(id = "newest"), episode(id = "older")))
    )

    private fun browseSearch(searchDao: FakeSearchDao = search, browseDao: FakeBrowseDao = browse): BrowseSearch {
        val dispatcher = UnconfinedTestDispatcher()
        return BrowseSearch(
            searchDao,
            browseDao,
            BrowseStyle { hash -> hash?.let { Uri.parse("content://art/$it") } },
            TEST_LABELS,
            CoroutineDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
        )
    }

    private fun MediaItem.group(): String? = mediaMetadata.extras?.getString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE)

    @Test
    fun `slow finds the artist and the song in sections with group titles in order`() = runTest {
        val results = browseSearch().search("slow")

        assertEquals(listOf("Artists", "Albums", "Songs", "Podcasts"), results.map { it.group() }.distinct())
        val artists = results.filter { it.group() == "Artists" }
        assertTrue("bocan/artist/11" in artists.map { it.mediaId })
        assertTrue(artists.all { it.mediaMetadata.isBrowsable == true })
        val songs = results.filter { it.group() == "Songs" }
        assertTrue("Souvlaki Space Station" in songs.map { it.mediaMetadata.title.toString() })
        assertTrue(songs.all { it.mediaMetadata.isPlayable == true })
        assertEquals(listOf("bocan/show/5"), results.filter { it.group() == "Podcasts" }.map { it.mediaId })
    }

    @Test
    fun `an exact name outranks a prefix match`() = runTest {
        val results = browseSearch().search("slowdive")
        assertEquals(listOf("bocan/artist/11", "bocan/artist/10"), results.filter { it.group() == "Artists" }.map { it.mediaId })
        assertEquals("bocan/album/100", results.first { it.group() == "Albums" }.mediaId)
        assertEquals("track:3", browseSearch().search("slow").first { it.group() == "Songs" }.mediaId)
    }

    @Test
    fun `each section caps at the per section limit`() = runTest {
        val results = browseSearch().search("slow", perSection = 1)
        assertEquals(listOf("Artists", "Albums", "Songs", "Podcasts"), results.map { it.group() })
    }

    @Test
    fun `songs that are not downloaded are not offered`() = runTest {
        assertTrue(browseSearch().search("slowness").none { it.mediaId == "track:4" })
    }

    @Test
    fun `punctuation only and blank queries return empty`() = runTest {
        assertTrue(browseSearch().search("...").isEmpty())
        assertTrue(browseSearch().search("… !?").isEmpty())
        assertTrue(browseSearch().search("   ").isEmpty())
    }

    @Test
    fun `voice for an artist plays that artist's tracks in album order`() = runTest {
        assertEquals(listOf(MediaId.Track(1), MediaId.Track(2)), browseSearch().resolveVoice("slowdive"))
    }

    @Test
    fun `voice falls through artist, album, song, then show`() = runTest {
        val noArtistTracks = FakeSearchDao(
            tracks = listOf(alison),
            albums = listOf(AlbumEntity(101, "Souvlaki", "Slowdive", 1993, null, trackCount = 10))
        )
        assertEquals(listOf(MediaId.Track(2), MediaId.Track(1)), browseSearch(searchDao = noArtistTracks).resolveVoice("souvlaki"))
        assertEquals(listOf(MediaId.Track(2)), browseSearch().resolveVoice("alison"))
        assertEquals(listOf(MediaId.Episode("newest")), browseSearch().resolveVoice("fast talk"))
    }

    @Test
    fun `voice with no match resolves to nothing`() = runTest {
        assertTrue(browseSearch().resolveVoice("zzz nothing").isEmpty())
        assertTrue(browseSearch().resolveVoice("...").isEmpty())
    }

    @Test
    fun `an empty voice query plays recently played`() = runTest {
        val played = browse.copy(recentlyPlayed = listOf(slow, alison))
        assertEquals(listOf(MediaId.Track(3), MediaId.Track(2)), browseSearch(browseDao = played).resolveVoice("  "))
    }

    @Test
    fun `an empty voice query with no plays shuffles everything downloaded`() = runTest {
        val ids = browseSearch().resolveVoice("")
        assertEquals(setOf<MediaId>(MediaId.Track(1), MediaId.Track(2), MediaId.Track(3)), ids.toSet())
        assertEquals(3, ids.size)
    }

    @Test
    fun `a voice request is one item with an empty id and its query in the request metadata`() {
        fun request(query: String?): MediaItem = MediaItem.Builder()
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(query).build())
            .build()

        assertEquals("slowdive", BrowseSearch.voiceQuery(listOf(request("slowdive"))))
        assertEquals("", BrowseSearch.voiceQuery(listOf(request(null))))
        assertNull(BrowseSearch.voiceQuery(listOf(MediaItem.Builder().setMediaId("track:1").build())))
        assertNull(BrowseSearch.voiceQuery(listOf(request("a"), request("b"))))
        val withUri = MediaItem.Builder()
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse("content://x")).build())
            .build()
        assertNull(BrowseSearch.voiceQuery(listOf(withUri)))
    }
}
