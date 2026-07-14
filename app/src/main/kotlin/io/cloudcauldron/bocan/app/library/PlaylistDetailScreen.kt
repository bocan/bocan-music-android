package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.PlayShuffleRow
import io.cloudcauldron.bocan.app.components.ReadableWidth
import io.cloudcauldron.bocan.app.components.TrackList
import kotlinx.coroutines.flow.StateFlow

/** Playlist detail: tracks in the Mac's position order, with a smart-list note. Read-only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    state: StateFlow<PlaylistDetailUiState>,
    callbacks: LibraryCallbacks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui by state.collectAsState()
    val ids = ui.tracks.map { it.id }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(ui.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { padding ->
        ReadableWidth(modifier = Modifier.padding(padding)) { readable ->
            TrackList(
                tracks = ui.tracks,
                callbacks = callbacks,
                modifier = readable
            ) {
                item(key = "header", contentType = "header") {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        if (ui.smart) {
                            Text(
                                text = stringResource(R.string.playlist_smart_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PlayShuffleRow(
                            onPlay = { callbacks.playContext(ids, 0) },
                            onShuffle = { callbacks.shuffle(ids) }
                        )
                    }
                }
            }
        }
    }
}
