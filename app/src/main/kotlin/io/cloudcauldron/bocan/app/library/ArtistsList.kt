package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import io.cloudcauldron.bocan.app.R

/** Artists with a derived album count; tapping opens the artist detail. */
@Composable
fun ArtistsList(artists: List<ArtistUi>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(artists, key = { it.id }, contentType = { "artist" }) { artist ->
            ListItem(
                headlineContent = { Text(artist.name) },
                supportingContent = {
                    Text(pluralStringResource(R.plurals.album_count, artist.albumCount, artist.albumCount))
                },
                modifier = Modifier.fillMaxWidth().clickable { onOpen(artist.id) }
            )
        }
    }
}
