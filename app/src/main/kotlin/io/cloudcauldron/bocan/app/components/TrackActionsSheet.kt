package io.cloudcauldron.bocan.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.library.LibraryCallbacks
import io.cloudcauldron.bocan.app.library.TrackUi

/**
 * The long-press action sheet for a track: Play next, Add to queue, Go to album, Go to
 * artist. Every action is a read or a queue op; nothing here writes to the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(track: TrackUi, callbacks: LibraryCallbacks, onDismiss: () -> Unit) {
    fun dismissing(block: () -> Unit): () -> Unit = {
        block()
        onDismiss()
    }
    // Fully expanded: at the half-height stop a landscape phone showed two of the four actions.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { SheetDragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            SheetAction(
                Icons.AutoMirrored.Rounded.PlaylistPlay,
                R.string.action_play_next,
                E2ETags.TrackActions.PLAY_NEXT,
                dismissing {
                    callbacks.playNext(listOf(track.id))
                }
            )
            SheetAction(
                Icons.AutoMirrored.Rounded.QueueMusic,
                R.string.action_add_to_queue,
                E2ETags.TrackActions.ADD_TO_QUEUE,
                dismissing {
                    callbacks.addToQueue(listOf(track.id))
                }
            )
            SheetAction(
                Icons.Rounded.Album,
                R.string.action_go_to_album,
                E2ETags.TrackActions.GO_TO_ALBUM,
                dismissing { callbacks.openAlbum(track.albumId) }
            )
            SheetAction(
                Icons.Rounded.Person,
                R.string.action_go_to_artist,
                E2ETags.TrackActions.GO_TO_ARTIST,
                dismissing { callbacks.openArtist(track.albumArtistId) }
            )
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, labelRes: Int, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().testTag(tag).clickable(onClick = onClick)
    )
}
