package io.cloudcauldron.bocan.app.library

import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.persistence.model.TrackSort
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A genre's albums and tracks, filtered from the library in memory (the DB has no genre join). */
data class GenreDetailUiState(val genre: String = "", val albums: List<AlbumUi> = emptyList(), val tracks: List<TrackUi> = emptyList())

/** Drives the genre detail screen: every track tagged with the genre, and the albums they span. */
class GenreDetailViewModel(private val genre: String, libraryDao: LibraryDao, dispatchers: CoroutineDispatchers) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val state: StateFlow<GenreDetailUiState> =
        libraryDao.observeAllTracks(TrackSort.Album)
            .map { tracks ->
                val inGenre = tracks.filter { it.genre == genre }
                val albums = inGenre
                    .distinctBy { it.albumId }
                    .map { AlbumUi(it.albumId, it.albumName, it.albumArtistName, it.year, it.artworkHash, 0) }
                GenreDetailUiState(genre = genre, albums = albums, tracks = inGenre.map { it.toUi() })
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), GenreDetailUiState(genre = genre))

    fun dispose() = scope.cancel()

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
