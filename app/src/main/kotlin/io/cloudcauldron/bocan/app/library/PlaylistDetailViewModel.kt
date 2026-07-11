package io.cloudcauldron.bocan.app.library

import io.cloudcauldron.bocan.persistence.daos.PlaylistDao
import io.cloudcauldron.bocan.persistence.model.PlaylistKind
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A playlist's tracks in the Mac's order, with its name and kind. Read-only: order is not editable. */
data class PlaylistDetailUiState(
    val name: String = "",
    val smart: Boolean = false,
    val accentColor: String? = null,
    val tracks: List<TrackUi> = emptyList()
)

/** Drives the playlist detail screen. Tracks come in the manifest's position order. */
class PlaylistDetailViewModel(playlistId: Long, playlistDao: PlaylistDao, dispatchers: CoroutineDispatchers) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val state: StateFlow<PlaylistDetailUiState> =
        combine(playlistDao.observePlaylistTree(), playlistDao.observeTracksIn(playlistId)) { tree, tracks ->
            val playlist = tree.firstOrNull { it.id == playlistId }
            PlaylistDetailUiState(
                name = playlist?.name.orEmpty(),
                smart = playlist?.kind == PlaylistKind.Smart,
                accentColor = playlist?.accentColor,
                tracks = tracks.map { it.toUi() }
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), PlaylistDetailUiState())

    fun dispose() = scope.cancel()

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
