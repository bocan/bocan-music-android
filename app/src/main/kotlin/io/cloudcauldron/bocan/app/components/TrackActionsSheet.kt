package io.cloudcauldron.bocan.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.cloudcauldron.bocan.app.R
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            SheetAction(
                Icons.AutoMirrored.Rounded.PlaylistPlay,
                R.string.action_play_next,
                dismissing {
                    callbacks.playNext(listOf(track.id))
                }
            )
            SheetAction(
                Icons.AutoMirrored.Rounded.QueueMusic,
                R.string.action_add_to_queue,
                dismissing {
                    callbacks.addToQueue(listOf(track.id))
                }
            )
            SheetAction(Icons.Rounded.Album, R.string.action_go_to_album, dismissing { callbacks.openAlbum(track.albumId) })
            SheetAction(Icons.Rounded.Person, R.string.action_go_to_artist, dismissing { callbacks.openArtist(track.albumArtistId) })
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}
