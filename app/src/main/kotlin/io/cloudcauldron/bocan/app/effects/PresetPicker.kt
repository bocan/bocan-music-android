package io.cloudcauldron.bocan.app.effects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.playback.audio.EqPreset

/**
 * The preset row: a horizontally scrolling set of chips (built-ins first, then the user's
 * own), plus save-as. A user preset chip carries a delete affordance; a built-in never
 * does. When the current curve matches no preset, no chip is selected and the caller shows
 * the custom label.
 */
@Composable
fun PresetPicker(
    presets: List<EqPreset>,
    activePresetId: String?,
    onSelect: (EqPreset) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSave by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = preset.id == activePresetId,
                onClick = { onSelect(preset) },
                label = { Text(presetDisplayName(preset)) },
                trailingIcon = deleteIcon(preset, onDelete)
            )
        }
        TextButton(onClick = { showSave = true }) { Text(stringResource(R.string.eq_save_preset)) }
    }

    if (showSave) {
        SavePresetDialog(
            onConfirm = { name ->
                onSave(name)
                showSave = false
            },
            onDismiss = { showSave = false }
        )
    }
}

private fun deleteIcon(preset: EqPreset, onDelete: (String) -> Unit): (@Composable () -> Unit)? {
    if (preset.isBuiltIn) return null
    return {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.eq_delete_preset),
            // Pad inside the clickable so the touch target reaches 48 dp.
            modifier = Modifier
                .clickable { onDelete(preset.id) }
                .padding(start = 4.dp)
                .minimumInteractiveComponentSize()
        )
    }
}

@Composable
private fun SavePresetDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eq_save_preset)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.eq_preset_name_hint)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.eq_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.eq_cancel)) } }
    )
}

/**
 * Built-in preset names ship as string resources so they reach translators; the
 * stable ids come from core. A user-saved preset keeps the name the user typed.
 */
@Composable
private fun presetDisplayName(preset: EqPreset): String {
    val res = BUILT_IN_NAMES[preset.id] ?: return preset.name
    return stringResource(res)
}

private val BUILT_IN_NAMES = mapOf(
    "bocan.flat" to R.string.eq_preset_flat,
    "bocan.rock" to R.string.eq_preset_rock,
    "bocan.jazz" to R.string.eq_preset_jazz,
    "bocan.classical" to R.string.eq_preset_classical,
    "bocan.electronic" to R.string.eq_preset_electronic,
    "bocan.vocal_boost" to R.string.eq_preset_vocal_boost,
    "bocan.bass_boost" to R.string.eq_preset_bass_boost,
    "bocan.treble_boost" to R.string.eq_preset_treble_boost,
    "bocan.loudness" to R.string.eq_preset_loudness,
    "bocan.spoken_word" to R.string.eq_preset_spoken_word
)
