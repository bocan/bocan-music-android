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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R

/**
 * One episode row: title, published date, duration, and a listening indicator (unplayed
 * dot, in-progress ring with remaining time, or a played check with dimming). Tap plays;
 * an overflow menu marks played or unplayed and opens show notes.
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (played) DIMMED else 1f)
    ) {
        ProgressIndicator(episode.progress)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(episode.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = subtitle(episode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        EpisodeMenu(onMarkPlayed, onMarkUnplayed, onShowNotes)
    }
}

@Composable
private fun ProgressIndicator(progress: EpisodeProgressUi) {
    when (progress) {
        EpisodeProgressUi.Unplayed -> Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = stringResource(R.string.episode_unplayed),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            modifier = Modifier.size(20.dp)
        )
        is EpisodeProgressUi.InProgress -> CircularProgressIndicator(
            progress = { progress.progress },
            modifier = Modifier.size(20.dp)
        )
        EpisodeProgressUi.Played -> Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = stringResource(R.string.episode_played),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
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
        is EpisodeProgressUi.InProgress -> stringResource(
            R.string.episode_remaining,
            date,
            (progress.remainingMs / MS_PER_MINUTE).toInt()
        )
        else -> stringResource(R.string.episode_date_duration, date, DateUtils.formatElapsedTime(episode.durationMs / MS_PER_SECOND))
    }
}

private const val DIMMED = 0.55f
private const val MS_PER_SECOND = 1000L
private const val MS_PER_MINUTE = 60_000L
