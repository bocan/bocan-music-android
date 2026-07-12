package io.cloudcauldron.bocan.app.podcasts

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.playback.podcast.Chapter

/** The chapters list: tap a chapter to seek there, with the active chapter highlighted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(chapters: List<Chapter>, activeIndex: Int, onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
    val playingNow = stringResource(R.string.chapter_playing_a11y)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            itemsIndexed(chapters, key = { index, _ -> index }) { index, chapter ->
                val active = index == activeIndex
                ListItem(
                    headlineContent = { Text(chapter.title) },
                    trailingContent = { Text(DateUtils.formatElapsedTime(chapter.startTimeMs / MS_PER_SECOND)) },
                    colors = if (active) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSeek(chapter.startTimeMs)
                            onDismiss()
                        }
                        .semantics { if (active) stateDescription = playingNow }
                )
            }
        }
    }
}

private const val MS_PER_SECOND = 1000L
