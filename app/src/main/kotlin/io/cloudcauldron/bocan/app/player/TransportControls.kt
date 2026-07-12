package io.cloudcauldron.bocan.app.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.playback.queue.RepeatMode

/**
 * The transport row: shuffle, previous, a large play or pause, next, and repeat.
 * Play/pause exposes its state to TalkBack. A long-press on shuffle offers the smart
 * strategy via [onShuffleLongPress].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransportControls(
    isPlaying: Boolean,
    repeatMode: RepeatMode,
    shuffleActive: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onShuffleLongPress: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shuffleTint = if (shuffleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        val shuffleState = stringResource(if (shuffleActive) R.string.state_on else R.string.state_off)
        // A 48 dp target with the icon centred; tint alone never carries the state.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onShuffle,
                    onLongClick = onShuffleLongPress
                )
                .size(48.dp)
                .semantics { stateDescription = shuffleState }
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.action_shuffle),
                tint = shuffleTint,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.action_previous))
        }
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
            if (isPlaying) {
                Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.action_pause))
            } else {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_play))
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.action_next))
        }
        val repeatActive = repeatMode != RepeatMode.Off
        val repeatState = stringResource(
            when (repeatMode) {
                RepeatMode.Off -> R.string.repeat_state_off
                RepeatMode.All -> R.string.repeat_state_all
                RepeatMode.One -> R.string.repeat_state_one
            }
        )
        IconButton(onClick = onCycleRepeat, modifier = Modifier.semantics { stateDescription = repeatState }) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = stringResource(R.string.action_repeat),
                tint = if (repeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The podcast transport row: skip-back, a large play or pause, skip-forward, and a speed
 * chip that cycles the presets. Previous and next stay reachable through the queue sheet.
 */
@Composable
fun PodcastTransportControls(
    isPlaying: Boolean,
    speed: Float,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = onCycleSpeed,
            label = { Text(stringResource(R.string.speed_value, speed)) }
        )
        IconButton(onClick = onSkipBack) {
            Icon(Icons.Rounded.Replay10, contentDescription = stringResource(R.string.action_skip_back))
        }
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
            if (isPlaying) {
                Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.action_pause))
            } else {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_play))
            }
        }
        IconButton(onClick = onSkipForward) {
            Icon(Icons.Rounded.Forward30, contentDescription = stringResource(R.string.action_skip_forward))
        }
    }
}
