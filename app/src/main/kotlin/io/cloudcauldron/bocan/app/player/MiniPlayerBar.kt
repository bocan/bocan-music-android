package io.cloudcauldron.bocan.app.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.ArtworkImage
import io.cloudcauldron.bocan.playback.queue.PlayerUiState

/**
 * The persistent mini player docked above the bottom bar whenever a session item
 * exists: artwork, a marqueed title and artist, play or pause, and a progress hairline.
 * Tapping the bar opens Now Playing (built in phase 06).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerBar(state: PlayerUiState, onPlayPause: () -> Unit, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val current = state.current ?: return
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val reducedMotion = isReducedMotion(LocalContext.current)
    val barDescription = stringResource(R.string.mini_player_a11y, current.title, current.artist.orEmpty())
    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTap)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                ArtworkImage(
                    artworkHash = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(10.dp))
                // One TalkBack sentence for the whole bar; the play button keeps its own.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { contentDescription = barDescription }
                ) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (reducedMotion) Modifier else Modifier.basicMarquee()
                    )
                    current.artist?.let { artist ->
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onPlayPause) {
                    if (state.isPlaying) {
                        Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.action_pause))
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_play))
                    }
                }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}
