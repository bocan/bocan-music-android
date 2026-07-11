package io.cloudcauldron.bocan.app.player

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.playback.SleepDuration
import io.cloudcauldron.bocan.playback.SleepTimerState

private val PRESET_MINUTES = listOf(15, 30, 45, 60)

/**
 * The sleep timer sheet: fixed presets plus End of track, a live countdown when armed,
 * and extend or cancel. Starting a timer fades the music out rather than cutting.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onStart: (SleepDuration) -> Unit,
    onExtend: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            Text(stringResource(R.string.sleep_timer_title), modifier = Modifier.padding(bottom = 12.dp))
            when (state) {
                is SleepTimerState.Counting -> ArmedControls(
                    label = stringResource(
                        R.string.sleep_timer_remaining,
                        DateUtils.formatElapsedTime(state.remainingMs / 1000)
                    ),
                    onExtend = onExtend,
                    onCancel = onCancel
                )
                SleepTimerState.WaitingForTrackEnd -> ArmedControls(
                    label = stringResource(R.string.sleep_timer_end_of_track_armed),
                    onExtend = onExtend,
                    onCancel = onCancel
                )
                SleepTimerState.Fading -> Text(stringResource(R.string.sleep_timer_fading))
                SleepTimerState.Idle -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESET_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = false,
                            onClick = { onStart(SleepDuration.Fixed(minutes * MINUTE_MS)) },
                            label = { Text(stringResource(R.string.sleep_timer_minutes, minutes)) }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { onStart(SleepDuration.EndOfTrack) },
                        label = { Text(stringResource(R.string.sleep_timer_end_of_track)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArmedControls(label: String, onExtend: (Int) -> Unit, onCancel: () -> Unit) {
    Column {
        Text(label, modifier = Modifier.padding(bottom = 12.dp))
        Button(onClick = { onExtend(EXTEND_MINUTES) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sleep_timer_extend, EXTEND_MINUTES))
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.sleep_timer_cancel))
        }
    }
}

private const val MINUTE_MS = 60_000L
private const val EXTEND_MINUTES = 5
