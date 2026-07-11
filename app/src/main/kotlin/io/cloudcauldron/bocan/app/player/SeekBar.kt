package io.cloudcauldron.bocan.app.player

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

/**
 * The seek bar. The thumb follows the player position (ticked from PlayerUiState) until
 * the user drags, when it previews the drag position and only seeks on release, so
 * scrubbing does not fight the position ticker. Elapsed and remaining times flank it.
 */
@Composable
fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val liveFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val fraction = if (dragging) dragFraction else liveFraction
    val shownMs = (fraction * durationMs).toLong()

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = {
                dragging = true
                dragFraction = it
            },
            onValueChangeFinished = {
                onSeek((dragFraction * durationMs).toLong())
                dragging = false
            }
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = formatTime(shownMs))
            Text(
                text = "-" + formatTime((durationMs - shownMs).coerceAtLeast(0)),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private const val MS_PER_SECOND = 1000L

private fun formatTime(ms: Long): String = DateUtils.formatElapsedTime(ms / MS_PER_SECOND)
