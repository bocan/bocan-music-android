package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The list of genres; tapping opens the genre detail. */
@Composable
fun GenresList(genres: List<String>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(genres, key = { it }, contentType = { "genre" }) { genre ->
            ListItem(
                headlineContent = { Text(genre) },
                modifier = Modifier.fillMaxWidth().clickable { onOpen(genre) }
            )
        }
    }
}
