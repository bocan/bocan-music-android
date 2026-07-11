package io.cloudcauldron.bocan.app.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R

private const val MIN_SPEED = 0.5f
private const val MAX_SPEED = 2.0f
private const val STEP = 0.1f
private const val NORMAL_SPEED = 1.0f

/** A speed stepper from 0.5x to 2.0x (pitch preserved by the player), with a reset to 1.0x. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(speed: Float, onSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
        ) {
            Text(stringResource(R.string.speed_title), style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                FilledIconButton(onClick = { onSpeed((speed - STEP).coerceIn(MIN_SPEED, MAX_SPEED)) }) {
                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.speed_slower))
                }
                Text(stringResource(R.string.speed_value, speed), style = MaterialTheme.typography.headlineSmall)
                FilledIconButton(onClick = { onSpeed((speed + STEP).coerceIn(MIN_SPEED, MAX_SPEED)) }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.speed_faster))
                }
            }
            TextButton(onClick = { onSpeed(NORMAL_SPEED) }) { Text(stringResource(R.string.speed_reset)) }
        }
    }
}
