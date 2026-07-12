package io.cloudcauldron.bocan.app.podcasts

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R

/**
 * One episode row: title, published date, duration, and a listening indicator (unplayed
 * ring, in-progress ring with remaining time, or a played check). Tap plays; an overflow
 * menu marks played or unplayed and opens show notes. The info block merges into one
 * TalkBack sentence; the overflow button stays its own target. Played rows dim their
 * artwork indicator only, never the text, so contrast holds in both themes.
 */
@Composable
fun EpisodeRow(
    episode: EpisodeUi,
    onPlay: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
    onShowNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val played = episode.progress is EpisodeProgressUi.Played
    val rowDescription = rowDescription(episode)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { contentDescription = rowDescription }
        ) {
            ProgressIndicator(episode.progress)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (played) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle(episode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        EpisodeMenu(onMarkPlayed, onMarkUnplayed, onShowNotes)
    }
}

@Composable
private fun ProgressIndicator(progress: EpisodeProgressUi) {
    when (progress) {
        EpisodeProgressUi.Unplayed -> Icon(
            Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
        is EpisodeProgressUi.InProgress -> CircularProgressIndicator(
            progress = { progress.progress },
            modifier = Modifier.size(20.dp)
        )
        EpisodeProgressUi.Played -> Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = PLAYED_ICON_ALPHA),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** The one TalkBack sentence for the info block: title, date and time, listening state. */
@Composable
private fun rowDescription(episode: EpisodeUi): String {
    val state = when (episode.progress) {
        EpisodeProgressUi.Unplayed -> stringResource(R.string.episode_unplayed)
        is EpisodeProgressUi.InProgress -> stringResource(R.string.episode_in_progress)
        EpisodeProgressUi.Played -> stringResource(R.string.episode_played)
    }
    return stringResource(R.string.episode_row_a11y, episode.title, subtitle(episode), state)
}

@Composable
private fun EpisodeMenu(onMarkPlayed: () -> Unit, onMarkUnplayed: () -> Unit, onShowNotes: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.episode_mark_played)) }, onClick = {
            open = false
            onMarkPlayed()
        })
        DropdownMenuItem(text = { Text(stringResource(R.string.episode_mark_unplayed)) }, onClick = {
            open = false
            onMarkUnplayed()
        })
        DropdownMenuItem(text = { Text(stringResource(R.string.episode_show_notes)) }, onClick = {
            open = false
            onShowNotes()
        })
    }
}

@Composable
private fun subtitle(episode: EpisodeUi): String {
    val date = DateUtils.getRelativeTimeSpanString(episode.publishedAt.toEpochMilli()).toString()
    val progress = episode.progress
    return when (progress) {
        is EpisodeProgressUi.InProgress -> pluralStringResource(
            R.plurals.episode_remaining,
            (progress.remainingMs / MS_PER_MINUTE).toInt(),
            date,
            (progress.remainingMs / MS_PER_MINUTE).toInt()
        )
        else -> stringResource(R.string.episode_date_duration, date, DateUtils.formatElapsedTime(episode.durationMs / MS_PER_SECOND))
    }
}

private const val PLAYED_ICON_ALPHA = 0.8f
private const val MS_PER_SECOND = 1000L
private const val MS_PER_MINUTE = 60_000L
