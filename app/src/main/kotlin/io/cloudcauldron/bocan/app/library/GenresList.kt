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
import io.cloudcauldron.bocan.app.e2e.E2ETags

/** The list of genres with an A to Z rail; tapping opens the genre detail. */
@Composable
fun GenresList(genres: List<String>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val firstIndexByLetter = remember(genres) { railIndex(genres) }
    WithAlphabetRail(firstIndexByLetter, listState, modifier) { listModifier ->
        LazyColumn(state = listState, modifier = listModifier) {
            items(genres, key = { it }, contentType = { "genre" }) { genre ->
                ListItem(
                    headlineContent = { Text(genre) },
                    modifier = Modifier.fillMaxWidth().testTag(E2ETags.Rows.GENRE).clickable { onOpen(genre) }
                )
            }
        }
    }
}
