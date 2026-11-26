package io.cloudcauldron.bocan.playback.browse

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.daos.BrowseDao
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaId
import io.cloudcauldron.bocan.playback.episode
import io.cloudcauldron.bocan.playback.track
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
class MediaTreeTests {
    private val log = RecordingLog()

    private fun tree(dao: BrowseDao): MediaTree {
        val dispatcher = UnconfinedTestDispatcher()
        return MediaTree(
            dao,
            TEST_LABELS,
            BrowseStyle { null },
            CoroutineDispatchers(io = dispatcher, default = dispatcher, main = dispatcher),
            log
        )
    }

    private suspend fun MediaTree.all(parentId: String): List<MediaItem> = children(parentId, page = 0, pageSize = Int.MAX_VALUE)

    private fun MediaItem.browsableStyle(): Int? = mediaMetadata.extras?.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE)

    @Test
    fun `the root is four tabs in order`() = runTest {
        val tabs = tree(FakeBrowseDao()).all(MediaTree.ROOT_ID)
        assertEquals(
            listOf(MediaTree.CATEGORY_HOME, MediaTree.CATEGORY_PLAYLISTS, MediaTree.CATEGORY_ALBUMS, MediaTree.CATEGORY_ARTISTS),
            tabs.map { it.mediaId }
        )
        assertEquals(listOf("Home", "Playlists", "Albums", "Artists"), tabs.map { it.mediaMetadata.title.toString() })
        assertTrue(tabs.all { it.mediaMetadata.isBrowsable == true })
    }

    @Test
    fun `a root children limit under four collapses the root to three tabs`() = runTest {
        val tree = tree(FakeBrowseDao())
        assertEquals(
            listOf(MediaTree.CATEGORY_HOME, MediaTree.CATEGORY_PLAYLISTS, MediaTree.CATEGORY_ALBUMS),
            tree.children(MediaTree.ROOT_ID, 0, Int.MAX_VALUE, rootChildrenLimit = 3).map { it.mediaId }
        )
        assertEquals(4, tree.children(MediaTree.ROOT_ID, 0, Int.MAX_VALUE, rootChildrenLimit = 4).size)
        assertEquals(4, tree.rootCategories(rootChildrenLimit = null).size)
    }

    @Test
    fun `the root children limit is read from the root extras`() {
        assertEquals(3, MediaTree.rootChildrenLimit(Bundle().apply { putInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 3) }))
        assertNull(MediaTree.rootChildrenLimit(Bundle()))
        assertNull(MediaTree.rootChildrenLimit(null))
    }

    @Test
    fun `the second page of the root is empty`() = runTest {
        assertTrue(tree(FakeBrowseDao()).children(MediaTree.ROOT_ID, page = 1, pageSize = 50).isEmpty())
    }

    @Test
    fun `home holds recently played, continue listening, podcasts as a grid, and songs`() = runTest {
        val home = tree(FakeBrowseDao()).all(MediaTree.CATEGORY_HOME)
        assertEquals(
            listOf(MediaTree.CATEGORY_RECENT, MediaTree.CATEGORY_CONTINUE, MediaTree.CATEGORY_PODCASTS, MediaTree.CATEGORY_SONGS),
            home.map { it.mediaId }
        )
        assertEquals("Recently Played", home.first().mediaMetadata.title.toString())
        val podcasts = home.single { it.mediaId == MediaTree.CATEGORY_PODCASTS }
        assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM, podcasts.browsableStyle())
        assertEquals(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM, home.first().browsableStyle())
    }

    @Test
    fun `artists open to only the non empty buckets in order`() = runTest {
        val buckets = tree(FakeBrowseDao(artists = THIRTY_ARTISTS)).all(MediaTree.CATEGORY_ARTISTS)
        val expectedIds = listOf("A", "B", "M", "S", "0-9", "#").map { "${MediaTree.PREFIX_ARTIST_BUCKET}$it" }
        assertEquals(expectedIds, buckets.map { it.mediaId })
        assertEquals(listOf("A", "B", "M", "S", "Digits", "Other"), buckets.map { it.mediaMetadata.title.toString() })
        assertTrue(buckets.all { it.mediaMetadata.isBrowsable == true })
        assertTrue(buckets.all { it.browsableStyle() == MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM })
    }

    @Test
    fun `a bucket yields its artists alphabetically as artist folders`() = runTest {
        val dao = FakeBrowseDao(artists = THIRTY_ARTISTS)
        val bucket = tree(dao).all("${MediaTree.PREFIX_ARTIST_BUCKET}B")
        assertEquals(
            listOf("Bark Psychosis", "Bauhaus", "The Beatles", "Blur", "Boards of Canada"),
            bucket.map { it.mediaMetadata.title.toString() }
        )
        assertTrue(bucket.all { it.mediaId.startsWith("bocan/artist/") })

        val beatles = bucket.single { it.mediaMetadata.title.toString() == "The Beatles" }
        val withTracks = dao.copyWith(artistTracks = mapOf(idOf(beatles) to listOf(track(id = 77))))
        assertEquals(listOf(77L), tree(withTracks).all(beatles.mediaId).map { tapOf(it).trackId })
    }

    @Test
    fun `bucket ids are stable across calls`() = runTest {
        val tree = tree(FakeBrowseDao(artists = THIRTY_ARTISTS))
        suspend fun ids(node: String) = tree.all(node).map { it.mediaId }
        assertEquals(ids(MediaTree.CATEGORY_ARTISTS), ids(MediaTree.CATEGORY_ARTISTS))
        assertEquals(ids("${MediaTree.PREFIX_ARTIST_BUCKET}#"), ids("${MediaTree.PREFIX_ARTIST_BUCKET}#"))
    }

    @Test
    fun `an unknown bucket is empty`() = runTest {
        assertTrue(tree(FakeBrowseDao(artists = THIRTY_ARTISTS)).all("${MediaTree.PREFIX_ARTIST_BUCKET}Q").isEmpty())
    }

    @Test
    fun `albums open to buckets whose albums are a grid of covers`() = runTest {
        val dao = FakeBrowseDao(
            albums = listOf(
                AlbumEntity(3, "Souvlaki", "Slowdive", 1993, "AA", trackCount = 2),
                AlbumEntity(4, "Échos", "Someone", 2001, null, trackCount = 1)
            ),
            albumTracks = mapOf(3L to listOf(track(id = 30), track(id = 31)))
        )
        val style = BrowseStyle { hash -> hash?.let { android.net.Uri.parse("content://art/$it") } }
        val tree = MediaTree(dao, TEST_LABELS, style, unconfined(), log)

        val buckets = tree.all(MediaTree.CATEGORY_ALBUMS)
        assertEquals(listOf("bocan/albums/E", "bocan/albums/S"), buckets.map { it.mediaId })
        assertTrue(buckets.all { it.browsableStyle() == MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM })

        val albums = tree.all("bocan/albums/S")
        assertEquals(listOf("bocan/album/3"), albums.map { it.mediaId })
        assertEquals("content://art/AA", albums.single().mediaMetadata.artworkUri.toString())
        assertEquals(listOf(30L, 31L), tree.all("bocan/album/3").map { tapOf(it).trackId })
    }

    @Test
    fun `recently played is the newest fifty plays`() = runTest {
        val dao = FakeBrowseDao(recentlyPlayed = (1L..80L).map { track(id = it) })
        val tree = tree(dao)
        assertEquals((1L..50L).toList(), tree.all(MediaTree.CATEGORY_RECENT).map { tapOf(it).trackId })
        assertEquals((41L..50L).toList(), tree.children(MediaTree.CATEGORY_RECENT, page = 4, pageSize = 10).map { tapOf(it).trackId })
        assertTrue(tree.children(MediaTree.CATEGORY_RECENT, page = 5, pageSize = 10).isEmpty())
        assertTrue(log.warnings.isEmpty())
    }

    @Test
    fun `an unbounded request returns the whole node`() = runTest {
        val dao = FakeBrowseDao(playlistTracks = mapOf(9L to (1L..350L).map { track(id = it) }))
        assertEquals(350, tree(dao).all("bocan/playlist/9").size)
        assertTrue(log.warnings.isEmpty())
    }

    @Test
    fun `a node over the car cap logs a warning and returns the cap`() = runTest {
        val dao = FakeBrowseDao(playlistTracks = mapOf(9L to (1L..450L).map { track(id = it) }))
        assertEquals(MediaTree.CAR_LIST_CAP, tree(dao).all("bocan/playlist/9").size)
        val warning = log.warnings.single()
        assertEquals("browse.node.overCap", warning.first)
        assertEquals("bocan/playlist/9", warning.second["node"])
    }

    @Test
    fun `songs are bounded by definition to the newest cap and never warn`() = runTest {
        val dao = FakeBrowseDao(songs = (1L..450L).map { track(id = it) })
        val songs = tree(dao).all(MediaTree.CATEGORY_SONGS)
        assertEquals(MediaTree.CAR_LIST_CAP, songs.size)
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, songs.first().mediaMetadata.mediaType)
        assertTrue(log.warnings.isEmpty())
    }

    @Test
    fun `an explicit page size is honoured up to the explicit maximum`() = runTest {
        val dao = FakeBrowseDao(songs = (1L..10L).map { track(id = it) })
        tree(dao).children(MediaTree.CATEGORY_PODCASTS, page = 0, pageSize = 50)
        tree(dao).children(MediaTree.CATEGORY_PLAYLISTS, page = 2, pageSize = 3)
        assertEquals(3 to 6, dao.lastPlaylistsPaging)
        tree(dao).children(MediaTree.CATEGORY_PLAYLISTS, page = 1, pageSize = 900)
        assertEquals(MediaTree.MAX_EXPLICIT_PAGE to MediaTree.MAX_EXPLICIT_PAGE, dao.lastPlaylistsPaging)
    }

    @Test
    fun `invalid pages yield nothing`() = runTest {
        val tree = tree(FakeBrowseDao(songs = listOf(track(id = 1))))
        assertTrue(tree.children(MediaTree.CATEGORY_SONGS, page = -1, pageSize = 10).isEmpty())
        assertTrue(tree.children(MediaTree.CATEGORY_SONGS, page = 0, pageSize = 0).isEmpty())
        assertTrue(tree.children(MediaTree.CATEGORY_SONGS, page = 1, pageSize = Int.MAX_VALUE).isEmpty())
    }

    @Test
    fun `a show's children are playable episode items`() = runTest {
        val dao = FakeBrowseDao(
            shows = listOf(PodcastEntity(5, "The Slow Show", null, null, "SS", null)),
            episodesByShow = mapOf(5L to listOf(episode(id = "ep1", title = "Ep One")))
        )
        assertEquals(listOf("bocan/show/5"), tree(dao).all(MediaTree.CATEGORY_PODCASTS).map { it.mediaId })
        val items = tree(dao).all("bocan/show/5")
        assertEquals(listOf("episode:ep1"), items.map { it.mediaId })
        assertEquals(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE, items.single().mediaMetadata.mediaType)
    }

    @Test
    fun `an unknown parent id yields nothing`() = runTest {
        assertTrue(tree(FakeBrowseDao()).all("bocan/nonsense/9").isEmpty())
    }

    @Test
    fun `a tap on a song in an album, artist, or playlist queues that whole list`() = runTest {
        val songs = listOf(track(id = 1), track(id = 2), track(id = 3))
        val dao = FakeBrowseDao(
            albumTracks = mapOf(3L to songs),
            artistTracks = mapOf(4L to songs),
            playlistTracks = mapOf(5L to songs)
        )
        val tree = tree(dao)
        for (parent in listOf("bocan/album/3", "bocan/artist/4", "bocan/playlist/5")) {
            val tapped = tapOf(tree.all(parent)[1])
            assertEquals(parent, tapped.parentId)
            assertEquals(listOf<MediaId>(MediaId.Track(1), MediaId.Track(2), MediaId.Track(3)), tree.queueFor(tapped))
        }
    }

    @Test
    fun `a tap in recently played or songs queues that list`() = runTest {
        val dao = FakeBrowseDao(recentlyPlayed = listOf(track(id = 7), track(id = 8)), songs = listOf(track(id = 8), track(id = 9)))
        val tree = tree(dao)
        val recent = tapOf(tree.all(MediaTree.CATEGORY_RECENT).first())
        assertEquals(listOf<MediaId>(MediaId.Track(7), MediaId.Track(8)), tree.queueFor(recent))
        val song = tapOf(tree.all(MediaTree.CATEGORY_SONGS).last())
        assertEquals(listOf<MediaId>(MediaId.Track(8), MediaId.Track(9)), tree.queueFor(song))
    }

    @Test
    fun `two copies of a song in a playlist are different rows, even across pages`() = runTest {
        val dao = FakeBrowseDao(playlistTracks = mapOf(5L to listOf(track(id = 1), track(id = 2), track(id = 1))))
        val tree = tree(dao)
        assertEquals(listOf(0, 0, 1), tree.all("bocan/playlist/5").map { tapOf(it).occurrence })
        assertEquals(1, tapOf(tree.children("bocan/playlist/5", page = 1, pageSize = 2).single()).occurrence)
    }

    @Test
    fun `a song that left its list queues alone`() = runTest {
        val tree = tree(FakeBrowseDao(albumTracks = mapOf(3L to listOf(track(id = 1)))))
        assertEquals(listOf<MediaId>(MediaId.Track(9)), tree.queueFor(BrowseTap("bocan/album/3", 9)))
        assertEquals(listOf<MediaId>(MediaId.Track(9)), tree.queueFor(BrowseTap("bocan/nonsense/3", 9)))
    }

    @Test
    fun `a queue holds at most the car cap`() = runTest {
        val tree = tree(FakeBrowseDao(playlistTracks = mapOf(5L to (1L..450L).map { track(id = it) })))
        assertEquals(MediaTree.CAR_LIST_CAP, tree.queueFor(BrowseTap("bocan/playlist/5", 1)).size)
    }

    private fun tapOf(item: MediaItem): BrowseTap = assertNotNull(BrowseTap.parse(item.mediaId), "not a song row: ${item.mediaId}")

    private fun idOf(item: MediaItem): Long = item.mediaId.substringAfterLast('/').toLong()

    private fun unconfined(): CoroutineDispatchers {
        val dispatcher = UnconfinedTestDispatcher()
        return CoroutineDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
    }

    private companion object {
        /** Thirty artists across six buckets: A, B, M, S, 0-9, and #. */
        val THIRTY_ARTISTS: List<ArtistEntity> = listOf(
            "Air", "Aphex Twin", "Arcade Fire", "Arab Strap", "Autechre",
            "Bauhaus", "The Beatles", "Blur", "Boards of Canada", "Bark Psychosis",
            "Massive Attack", "Mogwai", "My Bloody Valentine", "Moderat", "M83",
            "Slowdive", "Swans", "Sigur Rós", "Spiritualized", "The Smiths",
            "2Pac", "10,000 Maniacs", "808 State", "65daysofstatic", "311",
            "…And You Will Know Us", "!!!", "Øyvind", "_underscore", "\"Quoted\""
        ).mapIndexed { index, name -> ArtistEntity(index + 1L, name) }
    }
}
