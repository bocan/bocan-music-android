package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.AlbumCell
import io.cloudcauldron.bocan.app.components.TrackList

/**
 * The shared artist and genre detail shape: a top bar, a horizontal row of the albums,
 * a "Songs" section header, then the tracks. Play behaviour comes from [TrackList].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsAndTracksDetail(
    title: String,
    albums: List<AlbumUi>,
    tracks: List<TrackUi>,
    callbacks: LibraryCallbacks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { padding ->
        TrackList(
            tracks = tracks,
            callbacks = callbacks,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (albums.isNotEmpty()) {
                item(key = "albums", contentType = "albums-row") {
                    LazyRow(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                        items(albums, key = { it.id }, contentType = { "album" }) { album ->
                            AlbumCell(
                                album = album,
                                onClick = { callbacks.openAlbum(album.id) },
                                modifier = Modifier.width(160.dp)
                            )
                        }
                    }
                }
            }
            item(key = "songs-title", contentType = "section") {
                Text(
                    text = stringResource(R.string.section_songs),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
