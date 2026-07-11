package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.DetailArtwork
import io.cloudcauldron.bocan.app.components.PlayShuffleRow
import io.cloudcauldron.bocan.app.components.TrackList
import kotlinx.coroutines.flow.StateFlow

/** Album detail: a header with artwork and Play or Shuffle, then the tracks in order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    state: StateFlow<AlbumDetailUiState>,
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
                title = { Text(ui.title, maxLines = 1) },
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { padding ->
        TrackList(
            tracks = ui.tracks,
            callbacks = callbacks,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item(key = "header", contentType = "header") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    DetailArtwork(ui.artworkHash)
                    Text(
                        text = ui.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    val subtitle = ui.year?.let { stringResource(R.string.album_cell_artist_year, ui.artist, it) } ?: ui.artist
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PlayShuffleRow(
                        onPlay = { callbacks.playContext(ids, 0) },
                        onShuffle = { callbacks.shuffle(ids) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}
