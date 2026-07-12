package io.cloudcauldron.bocan.app.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.TrackRow
import io.cloudcauldron.bocan.app.library.LibraryCallbacks

/**
 * FTS-backed search: one query box, results sectioned into tracks, albums, and artists
 * as you type. When the box is empty, recent searches show instead. The DAO sanitises
 * input, so odd characters search literally rather than erroring.
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel, callbacks: LibraryCallbacks, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsState()
    Column(modifier = modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 8.dp)) {
        OutlinedTextField(
            value = ui.query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { viewModel.onSubmit() }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        if (!ui.hasQuery) {
            RecentSearches(ui.recent, onTap = viewModel::onRecentTap, onClear = viewModel::clearRecent)
        } else {
            SearchResultsList(ui, callbacks)
        }
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onTap: (String) -> Unit, onClear: () -> Unit) {
    if (recent.isEmpty()) return
    Column {
        Text(
            text = stringResource(R.string.search_recent_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(8.dp)
        )
        recent.forEach { term ->
            ListItem(
                headlineContent = { Text(term) },
                modifier = Modifier.fillMaxWidth().clickable { onTap(term) }
            )
        }
        TextButton(onClick = onClear, modifier = Modifier.padding(4.dp)) {
            Text(stringResource(R.string.search_clear_recent))
        }
    }
}

@Composable
private fun SearchResultsList(ui: SearchUiState, callbacks: LibraryCallbacks) {
    if (ui.noResults) {
        Text(
            text = stringResource(R.string.search_no_results, ui.query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (ui.tracks.isNotEmpty()) {
            item(key = "tracks-h") { SectionHeader(R.string.section_songs) }
            items(ui.tracks, key = { "t${it.id}" }, contentType = { "track" }) { track ->
                TrackRow(
                    track = track,
                    onClick = { if (track.pending) callbacks.explainPending() else callbacks.playContext(listOf(track.id), 0) },
                    onLongClick = {}
                )
            }
        }
        if (ui.albums.isNotEmpty()) {
            item(key = "albums-h") { SectionHeader(R.string.section_albums) }
            items(ui.albums, key = { "a${it.id}" }, contentType = { "album" }) { album ->
                ListItem(
                    headlineContent = { Text(album.name) },
                    supportingContent = { Text(album.artist) },
                    modifier = Modifier.fillMaxWidth().clickable { callbacks.openAlbum(album.id) }
                )
            }
        }
        if (ui.artists.isNotEmpty()) {
            item(key = "artists-h") { SectionHeader(R.string.section_artists) }
            items(ui.artists, key = { "ar${it.id}" }, contentType = { "artist" }) { artist ->
                ListItem(
                    headlineContent = { Text(artist.name) },
                    modifier = Modifier.fillMaxWidth().clickable { callbacks.openArtist(artist.id) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { heading() }
    )
}
