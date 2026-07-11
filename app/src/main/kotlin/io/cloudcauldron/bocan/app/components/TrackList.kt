package io.cloudcauldron.bocan.app.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.cloudcauldron.bocan.app.library.LibraryCallbacks
import io.cloudcauldron.bocan.app.library.TrackUi

/**
 * A scrollable list of tracks with the standard tap and long-press behaviour, and the
 * long-press action sheet. Stable ids as keys and a single contentType keep scrolling
 * smooth. Tapping a pending track explains rather than plays. An optional [header]
 * scrolls with the list (used by detail screens).
 */
@Composable
fun TrackList(
    tracks: List<TrackUi>,
    callbacks: LibraryCallbacks,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    header: (LazyListScope.() -> Unit)? = null
) {
    var sheetTrack by remember { mutableStateOf<TrackUi?>(null) }
    val ids = remember(tracks) { tracks.map { it.id } }
    LazyColumn(state = listState, modifier = modifier) {
        header?.invoke(this)
        itemsIndexed(tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
            TrackRow(
                track = track,
                onClick = { if (track.pending) callbacks.explainPending() else callbacks.playContext(ids, index) },
                onLongClick = { sheetTrack = track }
            )
        }
    }
    sheetTrack?.let { selected ->
        TrackActionsSheet(selected, callbacks) { sheetTrack = null }
    }
}
