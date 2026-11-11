package io.cloudcauldron.bocan.app.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.BackButton
import io.cloudcauldron.bocan.app.components.DetailArtwork
import io.cloudcauldron.bocan.app.components.PlayShuffleRow
import io.cloudcauldron.bocan.app.components.ReadableWidth
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
                title = { Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    val onPlay = { callbacks.playContext(ids, 0) }
                    val onShuffle = { callbacks.shuffle(ids) }
                    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        // Landscape has little height: the cover stacked over the buttons pushed Play and
                        // every track below the fold, so the cover sits beside them instead.
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            DetailArtwork(ui.artworkHash)
                            Column(modifier = Modifier.weight(1f).padding(start = 24.dp)) {
                                AlbumHeaderText(ui, TextAlign.Start)
                                PlayShuffleRow(onPlay = onPlay, onShuffle = onShuffle)
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            DetailArtwork(ui.artworkHash)
                            AlbumHeaderText(ui, TextAlign.Center, Modifier.padding(top = 12.dp))
                            PlayShuffleRow(onPlay = onPlay, onShuffle = onShuffle)
                        }
                    }
                }
            }
        }
    }
}

/** The album's title and its artist and year, under the cover (portrait) or beside it (landscape). */
@Composable
private fun AlbumHeaderText(ui: AlbumDetailUiState, align: TextAlign, modifier: Modifier = Modifier) {
    val horizontal = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start
    Column(horizontalAlignment = horizontal, modifier = modifier) {
        Text(text = ui.title, style = MaterialTheme.typography.headlineSmall, textAlign = align)
        val subtitle = ui.year?.let { stringResource(R.string.album_cell_artist_year, ui.artist, it) } ?: ui.artist
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align
        )
    }
}
