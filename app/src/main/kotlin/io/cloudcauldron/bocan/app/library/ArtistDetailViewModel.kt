package io.cloudcauldron.bocan.app.library

import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.persistence.model.AlbumSort
import io.cloudcauldron.bocan.persistence.model.TrackSort
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** An artist's albums and all of their tracks. */
data class ArtistDetailUiState(val name: String = "", val albums: List<AlbumUi> = emptyList(), val tracks: List<TrackUi> = emptyList())

/**
 * Drives the artist detail screen. The artist's albums come from the album-artist name
 * match; their tracks are filtered from the all-tracks flow by albumArtistId in memory
 * (the DB has no per-artist track query, and this is a read-only browse).
 */
class ArtistDetailViewModel(artistId: Long, libraryDao: LibraryDao, dispatchers: CoroutineDispatchers) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val state: StateFlow<ArtistDetailUiState> =
        combine(
            libraryDao.observeArtists(),
            libraryDao.observeAlbums(AlbumSort.Name),
            libraryDao.observeAllTracks(TrackSort.Album)
        ) { artists, albums, tracks ->
            val name = artists.firstOrNull { it.id == artistId }?.name.orEmpty()
            ArtistDetailUiState(
                name = name,
                albums = albums.filter { it.albumArtistName == name }.map { it.toUi() },
                tracks = tracks.filter { it.albumArtistId == artistId }.map { it.toUi() }
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), ArtistDetailUiState())

    fun dispose() = scope.cancel()

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
