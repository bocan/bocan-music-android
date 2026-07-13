package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.components.TrackList
import kotlinx.coroutines.launch

/**
 * The full songs list with an alphabet rail for fast scrolling through 10k tracks. Row
 * rendering, tap, and long-press come from [TrackList] (stable keys, single contentType).
 */
@Composable
fun SongsList(songs: List<TrackUi>, callbacks: LibraryCallbacks, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstIndexByLetter = remember(songs) {
        buildMap {
            songs.forEachIndexed { index, song ->
                val letter = song.title.firstOrNull()?.uppercaseChar() ?: '#'
                putIfAbsent(letter, index)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        // Reserve the rail's width so a row's trailing duration never renders under the
        // alphabet letters at the right edge.
        TrackList(
            tracks = songs,
            callbacks = callbacks,
            listState = listState,
            modifier = Modifier.fillMaxSize().padding(end = RAIL_WIDTH)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(RAIL_WIDTH)
                .clearAndSetSemantics {}
        ) {
            ALPHABET.forEach { letter ->
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            firstIndexByLetter[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
                        }
                )
            }
        }
    }
}

private val RAIL_WIDTH = 24.dp
private val ALPHABET: List<Char> = ('A'..'Z').toList()
