package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.components.AlbumCell

/** Adaptive grid of album cells. Stable ids and a single contentType keep scroll smooth. */
@Composable
fun AlbumsGrid(albums: List<AlbumUi>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = modifier
    ) {
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            AlbumCell(album = album, onClick = { onOpen(album.id) })
        }
    }
}
