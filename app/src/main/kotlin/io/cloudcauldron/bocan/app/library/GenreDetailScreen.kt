package io.cloudcauldron.bocan.app.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/** Genre detail: the albums that span the genre, then every track tagged with it. */
@Composable
fun GenreDetailScreen(
    state: StateFlow<GenreDetailUiState>,
    callbacks: LibraryCallbacks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui by state.collectAsState()
    AlbumsAndTracksDetail(
        title = ui.genre,
        albums = ui.albums,
        tracks = ui.tracks,
        callbacks = callbacks,
        onBack = onBack,
        modifier = modifier
    )
}
