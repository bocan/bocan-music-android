package io.cloudcauldron.bocan.playback.browse

import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.daos.BrowseDao
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.episode
import io.cloudcauldron.bocan.playback.track
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MediaTreeTests {
    private val labels = BrowseLabels("Continue", "Playlists", "Albums", "Artists", "Podcasts", "Songs")

    private fun tree(dao: BrowseDao): MediaTree {
        val dispatcher = UnconfinedTestDispatcher()
        return MediaTree(dao, labels, artworkUri = { null }, CoroutineDispatchers(io = dispatcher, default = dispatcher, main = dispatcher))
    }

    @Test
    fun `the root holds the six categories in order`() = runTest {
        val children = tree(FakeBrowseDao()).children(MediaTree.ROOT_ID, page = 0, pageSize = 50)
        assertEquals(
            listOf(
                MediaTree.CATEGORY_CONTINUE,
                MediaTree.CATEGORY_PLAYLISTS,
                MediaTree.CATEGORY_ALBUMS,
                MediaTree.CATEGORY_ARTISTS,
                MediaTree.CATEGORY_PODCASTS,
                MediaTree.CATEGORY_SONGS
            ),
            children.map { it.mediaId }
        )
        assertTrue(children.all { it.mediaMetadata.isBrowsable == true })
    }

    @Test
    fun `the second page of the root is empty`() = runTest {
        assertTrue(tree(FakeBrowseDao()).children(MediaTree.ROOT_ID, page = 1, pageSize = 50).isEmpty())
    }

    @Test
    fun `songs are playable track items`() = runTest {
        val dao = FakeBrowseDao(songs = listOf(track(id = 7, title = "Hello")))
        val items = tree(dao).children(MediaTree.CATEGORY_SONGS, page = 0, pageSize = 50)
        assertEquals(listOf("track:7"), items.map { it.mediaId })
        assertTrue(items.single().mediaMetadata.isPlayable == true)
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, items.single().mediaMetadata.mediaType)
    }

    @Test
    fun `albums are browsable folders whose children are their tracks`() = runTest {
        val dao = FakeBrowseDao(
            albums = listOf(AlbumEntity(3, "Album Three", "AA", 2020, null, trackCount = 2)),
            albumTracks = mapOf(3L to listOf(track(id = 30), track(id = 31)))
        )
        val albums = tree(dao).children(MediaTree.CATEGORY_ALBUMS, page = 0, pageSize = 50)
        assertEquals(listOf("bocan/album/3"), albums.map { it.mediaId })
        val tracks = tree(dao).children("bocan/album/3", page = 0, pageSize = 50)
        assertEquals(listOf("track:30", "track:31"), tracks.map { it.mediaId })
    }

    @Test
    fun `a show's children are playable episode items`() = runTest {
        val dao = FakeBrowseDao(episodesByShow = mapOf(5L to listOf(episode(id = "ep1", title = "Ep One"))))
        val items = tree(dao).children("bocan/show/5", page = 0, pageSize = 50)
        assertEquals(listOf("episode:ep1"), items.map { it.mediaId })
        assertEquals(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE, items.single().mediaMetadata.mediaType)
    }

    @Test
    fun `paging passes limit and offset through to the dao`() = runTest {
        val dao = FakeBrowseDao(songs = (1L..10L).map { track(id = it) })
        tree(dao).children(MediaTree.CATEGORY_SONGS, page = 2, pageSize = 3)
        assertEquals(3 to 6, dao.lastSongsPaging)
    }

    @Test
    fun `an unknown parent id yields nothing`() = runTest {
        assertTrue(tree(FakeBrowseDao()).children("bocan/nonsense/9", page = 0, pageSize = 50).isEmpty())
    }
}

/** A hand-fed [BrowseDao] so the tree logic is tested without a real database. */
private class FakeBrowseDao(
    private val albums: List<AlbumEntity> = emptyList(),
    private val artists: List<ArtistEntity> = emptyList(),
    private val shows: List<PodcastEntity> = emptyList(),
    private val playlists: List<PlaylistEntity> = emptyList(),
    private val songs: List<TrackEntity> = emptyList(),
    private val albumTracks: Map<Long, List<TrackEntity>> = emptyMap(),
    private val artistTracks: Map<Long, List<TrackEntity>> = emptyMap(),
    private val playlistTracks: Map<Long, List<TrackEntity>> = emptyMap(),
    private val episodesByShow: Map<Long, List<EpisodeEntity>> = emptyMap(),
    private val continueListening: List<EpisodeEntity> = emptyList()
) : BrowseDao {
    var lastSongsPaging: Pair<Int, Int>? = null

    private fun <T> List<T>.page(limit: Int, offset: Int): List<T> = drop(offset).take(limit)

    override suspend fun albumsPage(limit: Int, offset: Int) = albums.page(limit, offset)
    override suspend fun artistsPage(limit: Int, offset: Int) = artists.page(limit, offset)
    override suspend fun showsPage(limit: Int, offset: Int) = shows.page(limit, offset)
    override suspend fun playlistsPage(limit: Int, offset: Int) = playlists.page(limit, offset)
    override suspend fun recentSongsPage(limit: Int, offset: Int): List<TrackEntity> {
        lastSongsPaging = limit to offset
        return songs.page(limit, offset)
    }

    override suspend fun albumTracksPage(albumId: Long, limit: Int, offset: Int) = albumTracks[albumId].orEmpty().page(limit, offset)
    override suspend fun artistTracksPage(artistId: Long, limit: Int, offset: Int) = artistTracks[artistId].orEmpty().page(limit, offset)
    override suspend fun playlistTracksPage(playlistId: Long, limit: Int, offset: Int) =
        playlistTracks[playlistId].orEmpty().page(limit, offset)

    override suspend fun episodesPage(podcastId: Long, limit: Int, offset: Int) = episodesByShow[podcastId].orEmpty().page(limit, offset)
    override suspend fun continueListeningPage(limit: Int, offset: Int) = continueListening.page(limit, offset)
}
