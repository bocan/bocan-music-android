package io.cloudcauldron.bocan.app.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.playback.lyrics.LyricLine
import io.cloudcauldron.bocan.playback.lyrics.LyricsDoc
import io.cloudcauldron.bocan.playback.lyrics.LyricsResult

/**
 * The lyrics pane. Synced lyrics scroll to keep the active line centred (jumping instead
 * of animating under reduced motion) and seek when a line is tapped; unsynced lyrics
 * scroll as plain text; absent lyrics show a quiet empty state. Sync is driven by the
 * player position via the active-line index, never a wall clock.
 */
@Composable
fun LyricsPane(state: LyricsUiState, onSeekToLine: (Long) -> Unit, modifier: Modifier = Modifier) {
    when (val result = state.result) {
        LyricsResult.None -> EmptyLyrics(modifier)
        is LyricsResult.Loaded -> when (val doc = result.doc) {
            is LyricsDoc.Synced -> SyncedLyrics(doc.lines, state.activeLineIndex, state.offsetMs, onSeekToLine, modifier)
            is LyricsDoc.Unsynced -> Text(
                text = doc.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
            )
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, activeIndex: Int, offsetMs: Long, onSeekToLine: (Long) -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    val reducedMotion = isReducedMotion(LocalContext.current)
    LaunchedEffect(activeIndex) {
        if (activeIndex < 0) return@LaunchedEffect
        if (reducedMotion) listState.scrollToItem(activeIndex) else listState.animateScrollToItem(activeIndex)
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 120.dp)) {
        if (offsetMs != 0L) {
            item(key = "offset") {
                Text(
                    text = stringResource(R.string.lyrics_offset, offsetMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
            val active = index == activeIndex
            Text(
                text = line.text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekToLine(line.timeMs) }
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun EmptyLyrics(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.lyrics_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
