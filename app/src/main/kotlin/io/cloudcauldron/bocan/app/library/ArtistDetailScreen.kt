package io.cloudcauldron.bocan.app.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/** Artist detail: the artist's albums in a row, then all of their tracks. */
@Composable
fun ArtistDetailScreen(
    state: StateFlow<ArtistDetailUiState>,
    callbacks: LibraryCallbacks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui by state.collectAsState()
    AlbumsAndTracksDetail(
        title = ui.name,
        albums = ui.albums,
        tracks = ui.tracks,
        callbacks = callbacks,
        onBack = onBack,
        modifier = modifier
    )
}
