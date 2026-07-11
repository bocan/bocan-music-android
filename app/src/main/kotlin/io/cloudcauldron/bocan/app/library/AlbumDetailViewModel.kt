package io.cloudcauldron.bocan.app.library

import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Header plus track list for one album; the header is derived from the album's tracks. */
data class AlbumDetailUiState(
    val title: String = "",
    val artist: String = "",
    val year: Int? = null,
    val artworkHash: String? = null,
    val tracks: List<TrackUi> = emptyList()
)

/** Drives the album detail screen: the tracks in disc and track order, and a derived header. */
class AlbumDetailViewModel(albumId: Long, libraryDao: LibraryDao, dispatchers: CoroutineDispatchers) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val state: StateFlow<AlbumDetailUiState> =
        libraryDao.observeTracksForAlbum(albumId)
            .map { tracks ->
                val first = tracks.firstOrNull()
                AlbumDetailUiState(
                    title = first?.albumName.orEmpty(),
                    artist = first?.albumArtistName.orEmpty(),
                    year = first?.year,
                    artworkHash = first?.artworkHash,
                    tracks = tracks.map { it.toUi() }
                )
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), AlbumDetailUiState())

    fun dispose() = scope.cancel()

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
