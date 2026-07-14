package io.cloudcauldron.bocan.playback

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.ArtistTrackCount
import io.cloudcauldron.bocan.persistence.model.DownloadCounts
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A LibraryDao stub that only answers tracksByIds, from a fixed set. */
private class FakeLibraryDao(tracks: List<TrackEntity>) : LibraryDao {
    private val byId = tracks.associateBy { it.id }
    override suspend fun tracksByIds(ids: List<Long>): List<TrackEntity> = ids.mapNotNull { byId[it] }
    override suspend fun downloadedTrackIds(): List<Long> = byId.keys.toList()

    override fun observeAlbumsByName(): Flow<List<AlbumEntity>> = emptyFlow()
    override fun observeAlbumsByArtist(): Flow<List<AlbumEntity>> = emptyFlow()
    override fun observeAlbumsByYear(): Flow<List<AlbumEntity>> = emptyFlow()
    override fun observeArtists(): Flow<List<ArtistEntity>> = emptyFlow()
    override fun observeArtistTrackCounts(): Flow<List<ArtistTrackCount>> = emptyFlow()
    override fun observeTracksForAlbum(albumId: Long): Flow<List<TrackEntity>> = emptyFlow()
    override fun observeAllTracksByTitle(): Flow<List<TrackEntity>> = emptyFlow()
    override fun observeAllTracksByArtist(): Flow<List<TrackEntity>> = emptyFlow()
    override fun observeAllTracksByAlbum(): Flow<List<TrackEntity>> = emptyFlow()
    override fun observeGenres(): Flow<List<String>> = emptyFlow()
    override fun observeDownloadCounts(): Flow<DownloadCounts> = emptyFlow()
}

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class DatabaseMediaItemSourceTests {
    private fun source(tracks: List<TrackEntity>): DatabaseMediaItemSource {
        val dispatcher = UnconfinedTestDispatcher()
        return DatabaseMediaItemSource(
            libraryDao = FakeLibraryDao(tracks),
            podcastDao = FakePodcastDao(),
            factory = MediaItemFactory(FakeMediaFileResolver()),
            dispatchers = CoroutineDispatchers(io = dispatcher, default = dispatcher, main = dispatcher),
            log = NoopLog
        )
    }

    @Test
    fun `resolveTracks preserves order and maps to media items`() = runTest {
        val source = source(listOf(track(id = 1), track(id = 2), track(id = 3)))
        val items = source.resolveTracks(listOf(3, 1, 2))
        assertEquals(listOf("track:3", "track:1", "track:2"), items.map { it.mediaId })
    }

    @Test
    fun `missing tracks are dropped rather than faked`() = runTest {
        val source = source(listOf(track(id = 1)))
        val items = source.resolveTracks(listOf(1, 99))
        assertEquals(listOf("track:1"), items.map { it.mediaId })
    }

    @Test
    fun `episode ids resolve to nothing until phase 07`() = runTest {
        val source = source(listOf(track(id = 1)))
        val items = source.resolve(listOf(MediaId.Track(1), MediaId.Episode("ep1")))
        assertEquals(listOf("track:1"), items.map { it.mediaId })
    }

    @Test
    fun `an episode resolves with its parent show's artwork`() = runTest {
        val dispatcher = UnconfinedTestDispatcher()
        val src = DatabaseMediaItemSource(
            libraryDao = FakeLibraryDao(emptyList()),
            // episode(id = "ep1") has podcastId = 1, so it draws show 1's cover.
            podcastDao = FakePodcastDao(
                episodes = listOf(episode(id = "ep1")),
                podcasts = listOf(
                    PodcastEntity(
                        id = 1,
                        title = "Show",
                        author = null,
                        descriptionHtml = null,
                        artworkHash = "showcover",
                        defaultSpeed = null
                    )
                )
            ),
            factory = MediaItemFactory(FakeMediaFileResolver()),
            dispatchers = CoroutineDispatchers(io = dispatcher, default = dispatcher, main = dispatcher),
            log = NoopLog
        )
        val items = src.resolveEpisodes(listOf("ep1"))
        assertEquals("file:///media/artwork/showcover", items.single().mediaMetadata.artworkUri?.toString())
    }
}
