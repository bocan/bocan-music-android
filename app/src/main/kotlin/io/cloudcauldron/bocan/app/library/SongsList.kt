package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.cloudcauldron.bocan.app.components.TrackList
import io.cloudcauldron.bocan.persistence.model.TrackSort

/**
 * The full songs list with an alphabet rail for fast scrolling through 10k tracks. Row
 * rendering, tap, and long-press come from [TrackList] (stable keys, single contentType).
 * The rail indexes what the list is sorted by ([sort]): titles, artists, or albums.
 */
@Composable
fun SongsList(songs: List<TrackUi>, sort: TrackSort, callbacks: LibraryCallbacks, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val firstIndexByLetter = remember(songs, sort) { railIndex(songs.map { it.railKey(sort) }) }
    WithAlphabetRail(firstIndexByLetter, listState, modifier) { listModifier ->
        TrackList(tracks = songs, callbacks = callbacks, listState = listState, modifier = listModifier)
    }
}

/** What the rail indexes under [sort]: the field the list is ordered by. */
internal fun TrackUi.railKey(sort: TrackSort): String = when (sort) {
    TrackSort.Title -> title
    TrackSort.Artist -> artist
    TrackSort.Album -> album
}
