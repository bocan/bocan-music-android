package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags

/** Artists with derived album and song counts, and an A to Z rail by name; tapping opens the artist detail. */
@Composable
fun ArtistsList(artists: List<ArtistUi>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val firstIndexByLetter = remember(artists) { railIndex(artists.map { it.name }) }
    WithAlphabetRail(firstIndexByLetter, listState, modifier) { listModifier ->
        LazyColumn(state = listState, modifier = listModifier) {
            items(artists, key = { it.id }, contentType = { "artist" }) { artist ->
                val albums = pluralStringResource(R.plurals.album_count, artist.albumCount, artist.albumCount)
                val songs = pluralStringResource(R.plurals.song_count, artist.songCount, artist.songCount)
                ListItem(
                    headlineContent = { Text(artist.name) },
                    supportingContent = { Text(stringResource(R.string.counts_joined, albums, songs)) },
                    modifier = Modifier.fillMaxWidth().testTag(E2ETags.Rows.ARTIST).clickable { onOpen(artist.id) }
                )
            }
        }
    }
}
