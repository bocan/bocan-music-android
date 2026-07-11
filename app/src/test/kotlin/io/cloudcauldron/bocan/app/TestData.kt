package io.cloudcauldron.bocan.app

import io.cloudcauldron.bocan.app.data.LibraryPreferencesSource
import io.cloudcauldron.bocan.app.data.RecentSearches
import io.cloudcauldron.bocan.app.library.LibraryTab
import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.persistence.daos.PlaylistDao
import io.cloudcauldron.bocan.persistence.daos.SearchDao
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.AlbumSort
import io.cloudcauldron.bocan.persistence.model.DownloadCounts
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.model.PlaylistKind
import io.cloudcauldron.bocan.persistence.model.SearchResults
import io.cloudcauldron.bocan.persistence.model.TrackSort
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun albumEntity(id: Long, name: String = "Album $id", artist: String = "Artist", year: Int? = 2000, tracks: Int = 5) =
    AlbumEntity(id, name, artist, year, "art$id", tracks)

fun artistEntity(id: Long, name: String) = ArtistEntity(id, name)

@Suppress("LongParameterList")
fun trackEntity(
    id: Long,
    title: String = "Track $id",
    albumId: Long = 1,
    albumArtistId: Long = 1,
    albumName: String = "Album",
    genre: String? = "Rock",
    relPath: String = "Artist/Album/$id.flac",
    downloadState: DownloadState = DownloadState.Downloaded
) = TrackEntity(
    id = id, title = title, artistId = 1, artistName = "Artist", albumArtistId = albumArtistId,
    albumArtistName = "Artist", albumId = albumId, albumName = albumName, trackNumber = id.toInt(),
    trackTotal = 10, discNumber = 1, discTotal = 1, year = 2000, genre = genre, composer = null, bpm = null,
    durationMs = 200_000, sampleRate = 44_100, bitDepth = 16, bitrate = null, channelCount = 2,
    isLossless = true, format = "flac", size = 1000, sha256 = "s$id", relPath = relPath, artworkHash = "art",
    lyricsHash = null, rating = 0, loved = false, rgTrackGain = null, rgTrackPeak = null, rgAlbumGain = null,
    rgAlbumPeak = null, clipSourceTrackId = null, clipStartMs = null, clipEndMs = null,
    downloadState = downloadState, syncedAt = Instant.EPOCH
)

fun playlistEntity(id: Long, name: String, kind: PlaylistKind = PlaylistKind.Manual, parentId: Long? = null) =
    PlaylistEntity(id, name, kind, parentId, sortOrder = 0, accentColor = null, artworkHash = null)

fun syncServerEntity() = SyncServerEntity("srv", "Mac", "fp", ByteArray(0), 1, Instant.EPOCH, Instant.EPOCH)

/** A LibraryDao backed by state flows the tests drive; per-sort queries share the backing list. */
class FakeLibraryDao : LibraryDao {
    val albumsFlow = MutableStateFlow<List<AlbumEntity>>(emptyList())
    val artistsFlow = MutableStateFlow<List<ArtistEntity>>(emptyList())
    val tracksFlow = MutableStateFlow<List<TrackEntity>>(emptyList())
    val genresFlow = MutableStateFlow<List<String>>(emptyList())
    val countsFlow = MutableStateFlow(DownloadCounts(0, 0, 0))

    override fun observeAlbumsByName(): Flow<List<AlbumEntity>> = albumsFlow
    override fun observeAlbumsByArtist(): Flow<List<AlbumEntity>> = albumsFlow
    override fun observeAlbumsByYear(): Flow<List<AlbumEntity>> = albumsFlow
    override fun observeArtists(): Flow<List<ArtistEntity>> = artistsFlow
    override fun observeTracksForAlbum(albumId: Long): Flow<List<TrackEntity>> =
        tracksFlow.map { list -> list.filter { it.albumId == albumId } }
    override fun observeAllTracksByTitle(): Flow<List<TrackEntity>> = tracksFlow
    override fun observeAllTracksByArtist(): Flow<List<TrackEntity>> = tracksFlow
    override fun observeAllTracksByAlbum(): Flow<List<TrackEntity>> = tracksFlow
    override fun observeGenres(): Flow<List<String>> = genresFlow
    override suspend fun tracksByIds(ids: List<Long>): List<TrackEntity> = tracksFlow.value.filter { it.id in ids }
    override suspend fun downloadedTrackIds(): List<Long> = tracksFlow.value.map { it.id }
    override fun observeDownloadCounts(): Flow<DownloadCounts> = countsFlow
}

/** A PlaylistDao backed by a state flow. */
class FakePlaylistDao : PlaylistDao {
    val playlistsFlow = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val tracksFlow = MutableStateFlow<List<TrackEntity>>(emptyList())
    override fun observePlaylistTree(): Flow<List<PlaylistEntity>> = playlistsFlow
    override fun observeTracksIn(playlistId: Long): Flow<List<TrackEntity>> = tracksFlow
}

/** A SearchDao that returns one track titled after the query, so tests can see which query won. */
class FakeSearchDao : SearchDao {
    var invocations = 0
        private set

    override fun search(query: String): Flow<SearchResults> {
        invocations++
        if (query.isBlank()) return flowOf(SearchResults.EMPTY)
        return flowOf(
            SearchResults(
                tracks = listOf(trackEntity(1, title = query)),
                albums = listOf(albumEntity(1, name = query)),
                artists = listOf(artistEntity(1, name = query))
            )
        )
    }

    override fun observeTrackMatches(match: String): Flow<List<TrackEntity>> = flowOf(emptyList())
    override fun observeAlbumMatches(match: String): Flow<List<AlbumEntity>> = flowOf(emptyList())
    override fun observeArtistMatches(match: String): Flow<List<ArtistEntity>> = flowOf(emptyList())
}

/** An in-memory preferences fake for the view models. */
class FakeLibraryPreferences(initialTab: LibraryTab = LibraryTab.Albums) : LibraryPreferencesSource {
    override val albumSort = MutableStateFlow(AlbumSort.Name)
    override val trackSort = MutableStateFlow(TrackSort.Title)
    override val lastTab = MutableStateFlow(initialTab)
    override val recentSearches = MutableStateFlow<List<String>>(emptyList())

    override suspend fun setAlbumSort(sort: AlbumSort) {
        albumSort.value = sort
    }
    override suspend fun setTrackSort(sort: TrackSort) {
        trackSort.value = sort
    }
    override suspend fun setLastTab(tab: LibraryTab) {
        lastTab.value = tab
    }
    override suspend fun addRecentSearch(query: String) {
        recentSearches.value = RecentSearches.updated(recentSearches.value, query)
    }
    override suspend fun clearRecentSearches() {
        recentSearches.value = emptyList()
    }
}
