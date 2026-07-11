package io.cloudcauldron.bocan.app.library

import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R

/** The scrollable secondary tab row across the six library views. */
@Composable
fun LibraryTabRow(selected: LibraryTab, onSelect: (LibraryTab) -> Unit) {
    ScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 8.dp) {
        LibraryTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(stringResource(tabLabel(tab))) }
            )
        }
    }
}

private fun tabLabel(tab: LibraryTab): Int = when (tab) {
    LibraryTab.Artists -> R.string.tab_artists
    LibraryTab.Albums -> R.string.tab_albums
    LibraryTab.Songs -> R.string.tab_songs
    LibraryTab.Genres -> R.string.tab_genres
    LibraryTab.Playlists -> R.string.tab_playlists
    LibraryTab.Folders -> R.string.tab_folders
}
