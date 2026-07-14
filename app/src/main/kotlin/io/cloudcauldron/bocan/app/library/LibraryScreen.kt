package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.EmptyState
import io.cloudcauldron.bocan.app.components.ReadableWidth
import io.cloudcauldron.bocan.app.components.ShuffleAllButton
import io.cloudcauldron.bocan.app.components.SortMenu
import io.cloudcauldron.bocan.app.components.SortOption
import io.cloudcauldron.bocan.persistence.model.AlbumSort
import io.cloudcauldron.bocan.persistence.model.TrackSort

/**
 * The Library destination: a title bar with a per-tab sort menu, the six-tab row, and
 * the selected tab's content, falling back to a first-run empty state when there is no
 * data yet. Collects the view model's per-tab flows so each is only active while shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    callbacks: LibraryCallbacks,
    emptyActions: LibraryEmptyActions,
    modifier: Modifier = Modifier
) {
    val status by viewModel.status.collectAsState()
    val tab by viewModel.selectedTab.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_library)) },
                actions = {
                    // Reads the ids on demand (see AppGraph.shuffleAllDownloaded), so it never
                    // holds the whole library in the UI, which matters at 10k+ tracks.
                    if (status == LibraryStatus.Content) ShuffleAllButton(callbacks.shuffleAll)
                    SortAction(tab, viewModel)
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (status) {
                LibraryStatus.NotPaired -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.library_empty_unpaired_title),
                    message = stringResource(R.string.library_empty_unpaired_message),
                    actionLabel = stringResource(R.string.home_pair_action),
                    onAction = emptyActions.onPair
                )
                LibraryStatus.Empty -> EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.library_empty_title),
                    message = stringResource(R.string.library_empty_message),
                    actionLabel = stringResource(R.string.sync_now_action),
                    onAction = emptyActions.onSyncNow
                )
                is LibraryStatus.Syncing -> EmptyState(
                    icon = Icons.Rounded.Sync,
                    title = stringResource(R.string.library_syncing_title),
                    message = stringResource(R.string.library_syncing_message)
                )
                LibraryStatus.Loading -> Unit
                LibraryStatus.Content -> {
                    LibraryTabRow(selected = tab, onSelect = viewModel::selectTab)
                    LibraryTabContent(tab, viewModel, callbacks)
                }
            }
        }
    }
}

@Composable
private fun SortAction(tab: LibraryTab, viewModel: LibraryViewModel) {
    when (tab) {
        LibraryTab.Albums -> {
            val sort by viewModel.albumSort.collectAsState()
            SortMenu(
                selected = sort,
                options = listOf(
                    SortOption(AlbumSort.Name, stringResource(R.string.sort_album_name)),
                    SortOption(AlbumSort.Artist, stringResource(R.string.sort_album_artist)),
                    SortOption(AlbumSort.Year, stringResource(R.string.sort_album_year))
                ),
                onSelect = viewModel::setAlbumSort
            )
        }
        LibraryTab.Songs -> {
            val sort by viewModel.trackSort.collectAsState()
            SortMenu(
                selected = sort,
                options = listOf(
                    SortOption(TrackSort.Title, stringResource(R.string.sort_song_title)),
                    SortOption(TrackSort.Artist, stringResource(R.string.sort_song_artist)),
                    SortOption(TrackSort.Album, stringResource(R.string.sort_song_album))
                ),
                onSelect = viewModel::setTrackSort
            )
        }
        else -> Unit
    }
}

@Composable
private fun LibraryTabContent(tab: LibraryTab, viewModel: LibraryViewModel, callbacks: LibraryCallbacks) {
    when (tab) {
        LibraryTab.Artists -> {
            val artists by viewModel.artists.collectAsState()
            ReadableWidth { m -> ArtistsList(artists, callbacks.openArtist, m) }
        }
        LibraryTab.Albums -> {
            // The grid, not a single-column list, so it wants the full width for more columns.
            val albums by viewModel.albums.collectAsState()
            AlbumsGrid(albums, callbacks.openAlbum, Modifier.fillMaxSize())
        }
        LibraryTab.Songs -> {
            // Collected only on this tab so the 10k+ TrackUi list is not held while browsing
            // albums, artists, or genres.
            val songs by viewModel.songs.collectAsState()
            ReadableWidth { m -> SongsList(songs, callbacks, m) }
        }
        LibraryTab.Genres -> {
            val genres by viewModel.genres.collectAsState()
            ReadableWidth { m -> GenresList(genres, callbacks.openGenre, m) }
        }
        LibraryTab.Playlists -> {
            val playlists by viewModel.playlists.collectAsState()
            ReadableWidth { m -> PlaylistsScreen(playlists, callbacks.openPlaylist, m) }
        }
        LibraryTab.Folders -> {
            val items by viewModel.folderItems.collectAsState()
            ReadableWidth { m -> FoldersScreen(items, callbacks, m) }
        }
    }
}

/** Actions the library's first-run empty states raise (pairing, sync). */
class LibraryEmptyActions(val onPair: () -> Unit = {}, val onSyncNow: () -> Unit = {})
