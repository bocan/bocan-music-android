package io.cloudcauldron.bocan.app.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A folder browser derived from track relPaths in memory (never from SQL). Tapping a
 * folder descends; system back ascends. Tapping a track plays the tracks in the current
 * folder from that position. The current path survives configuration changes.
 */
@Composable
fun FoldersScreen(items: List<FolderTree.Item>, callbacks: LibraryCallbacks, modifier: Modifier = Modifier) {
    var path by rememberSaveable { mutableStateOf(listOf<String>()) }
    val entries = remember(items, path) { FolderTree.childrenOf(items, path) }
    val trackIdsHere = remember(entries) {
        entries.filterIsInstance<FolderTree.Entry.Track>().map { it.trackId }
    }

    BackHandler(enabled = path.isNotEmpty()) { path = path.dropLast(1) }

    Column(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(entries, key = { entryKey(it) }, contentType = { entryType(it) }) { entry ->
                when (entry) {
                    is FolderTree.Entry.Folder -> ListItem(
                        headlineContent = { Text(entry.name) },
                        leadingContent = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable { path = entry.segments }
                    )
                    is FolderTree.Entry.Track -> {
                        val playIndex = trackIdsHere.indexOf(entry.trackId)
                        ListItem(
                            headlineContent = { Text(entry.name) },
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().clickable {
                                callbacks.playContext(trackIdsHere, playIndex.coerceAtLeast(0))
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun entryKey(entry: FolderTree.Entry): String = when (entry) {
    is FolderTree.Entry.Folder -> "folder:" + entry.segments.joinToString("/")
    is FolderTree.Entry.Track -> "track:" + entry.trackId
}

private fun entryType(entry: FolderTree.Entry): String = when (entry) {
    is FolderTree.Entry.Folder -> "folder"
    is FolderTree.Entry.Track -> "track"
}
