package io.cloudcauldron.bocan.playback.browse

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.persistence.daos.BrowseDao
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.NameRow
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
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
        assertEquals(listOf("track:77"), tree(withTracks).all(beatles.mediaId).map { it.mediaId })
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
        assertEquals(listOf("track:30", "track:31"), tree.all("bocan/album/3").map { it.mediaId })
    }

    @Test
    fun `recently played is the newest fifty plays`() = runTest {
        val dao = FakeBrowseDao(recentlyPlayed = (1L..80L).map { track(id = it) })
        val tree = tree(dao)
        assertEquals((1L..50L).map { "track:$it" }, tree.all(MediaTree.CATEGORY_RECENT).map { it.mediaId })
        assertEquals((41L..50L).map { "track:$it" }, tree.children(MediaTree.CATEGORY_RECENT, page = 4, pageSize = 10).map { it.mediaId })
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

    private fun idOf(item: MediaItem): Long = item.mediaId.substringAfterLast('/').toLong()

    private fun unconfined(): CoroutineDispatchers {
        val dispatcher = UnconfinedTestDispatcher()
        return CoroutineDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
    }

    private companion object {
        val TEST_LABELS = BrowseLabels(
            home = "Home",
            recentlyPlayed = "Recently Played",
            continueListening = "Continue",
            playlists = "Playlists",
            albums = "Albums",
            artists = "Artists",
            podcasts = "Podcasts",
            songs = "Songs",
            digitsBucket = "Digits",
            otherBucket = "Other"
        )

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

/** An [AppLog] that keeps warnings so a test can assert on them. */
private class RecordingLog : AppLog {
    val warnings = mutableListOf<Pair<String, Map<String, Any?>>>()

    override fun debug(event: String, fields: Map<String, Any?>) = Unit
    override fun info(event: String, fields: Map<String, Any?>) = Unit
    override fun warning(event: String, fields: Map<String, Any?>) {
        warnings += event to fields
    }
    override fun error(event: String, fields: Map<String, Any?>) = Unit
}

/** A hand-fed [BrowseDao] so the tree logic is tested without a real database. */
private data class FakeBrowseDao(
    private val albums: List<AlbumEntity> = emptyList(),
    private val artists: List<ArtistEntity> = emptyList(),
    private val shows: List<PodcastEntity> = emptyList(),
    private val playlists: List<PlaylistEntity> = emptyList(),
    private val songs: List<TrackEntity> = emptyList(),
    private val recentlyPlayed: List<TrackEntity> = emptyList(),
    private val albumTracks: Map<Long, List<TrackEntity>> = emptyMap(),
    private val artistTracks: Map<Long, List<TrackEntity>> = emptyMap(),
    private val playlistTracks: Map<Long, List<TrackEntity>> = emptyMap(),
    private val episodesByShow: Map<Long, List<EpisodeEntity>> = emptyMap(),
    private val continueListening: List<EpisodeEntity> = emptyList()
) : BrowseDao {
    var lastPlaylistsPaging: Pair<Int, Int>? = null

    fun copyWith(artistTracks: Map<Long, List<TrackEntity>>): FakeBrowseDao = copy(artistTracks = artistTracks)

    private fun <T> List<T>.page(limit: Int, offset: Int): List<T> = drop(offset).take(limit)

    override suspend fun albumsPage(limit: Int, offset: Int) = albums.page(limit, offset)
    override suspend fun artistsPage(limit: Int, offset: Int) = artists.page(limit, offset)
    override suspend fun showsPage(limit: Int, offset: Int) = shows.page(limit, offset)
    override suspend fun playlistsPage(limit: Int, offset: Int): List<PlaylistEntity> {
        lastPlaylistsPaging = limit to offset
        return playlists.page(limit, offset)
    }

    override suspend fun recentSongsPage(limit: Int, offset: Int) = songs.page(limit, offset)
    override suspend fun albumTracksPage(albumId: Long, limit: Int, offset: Int) = albumTracks[albumId].orEmpty().page(limit, offset)
    override suspend fun artistTracksPage(artistId: Long, limit: Int, offset: Int) = artistTracks[artistId].orEmpty().page(limit, offset)
    override suspend fun playlistTracksPage(playlistId: Long, limit: Int, offset: Int) =
        playlistTracks[playlistId].orEmpty().page(limit, offset)

    override suspend fun episodesPage(podcastId: Long, limit: Int, offset: Int) = episodesByShow[podcastId].orEmpty().page(limit, offset)
    override suspend fun continueListeningPage(limit: Int, offset: Int) = continueListening.page(limit, offset)
    override suspend fun artistNames() = artists.map { NameRow(it.id, it.name) }
    override suspend fun albumNames() = albums.map { NameRow(it.id, it.name) }
    override suspend fun artistsByIds(ids: List<Long>) = artists.filter { it.id in ids }.sortedBy { it.name.lowercase() }
    override suspend fun albumsByIds(ids: List<Long>) = albums.filter { it.id in ids }.sortedBy { it.name.lowercase() }
    override suspend fun recentlyPlayedPage(limit: Int, offset: Int) = recentlyPlayed.page(limit, offset)
    override suspend fun downloadedTrackIds() = songs.map { it.id }
}
