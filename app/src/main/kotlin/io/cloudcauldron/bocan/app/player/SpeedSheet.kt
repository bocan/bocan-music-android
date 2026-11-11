package io.cloudcauldron.bocan.app.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.SheetDragHandle
import io.cloudcauldron.bocan.app.e2e.E2ETags

private const val MIN_SPEED = 0.5f
private const val MAX_SPEED = 2.0f
private const val STEP = 0.1f
private const val NORMAL_SPEED = 1.0f

/** A speed stepper from 0.5x to 2.0x (pitch preserved by the player), with a reset to 1.0x. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(speed: Float, onSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    // Fully expanded: at the half-height stop a landscape phone cut off the reset button.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)
        ) {
            Text(stringResource(R.string.speed_title), style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                FilledIconButton(
                    onClick = { onSpeed((speed - STEP).coerceIn(MIN_SPEED, MAX_SPEED)) },
                    modifier = Modifier.testTag(E2ETags.Speed.SLOWER)
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.speed_slower))
                }
                val speedText = stringResource(R.string.speed_value, speed)
                Text(
                    text = speedText,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = speedText
                    }
                )
                FilledIconButton(
                    onClick = { onSpeed((speed + STEP).coerceIn(MIN_SPEED, MAX_SPEED)) },
                    modifier = Modifier.testTag(E2ETags.Speed.FASTER)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.speed_faster))
                }
            }
            TextButton(onClick = { onSpeed(NORMAL_SPEED) }, modifier = Modifier.testTag(E2ETags.Speed.RESET)) {
                Text(stringResource(R.string.speed_reset))
            }
        }
    }
}
