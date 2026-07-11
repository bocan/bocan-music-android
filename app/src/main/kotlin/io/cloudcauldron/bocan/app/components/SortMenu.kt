package io.cloudcauldron.bocan.app.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.cloudcauldron.bocan.app.R

/**
 * A sort affordance: an icon button that opens a menu of [options], the current
 * [selected] one marked. Generic so every list can reuse it (albums, songs, ...).
 */
@Composable
fun <T> SortMenu(selected: T, options: List<SortOption<T>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Sort,
            contentDescription = stringResource(R.string.sort_menu_a11y)
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                leadingIcon = { RadioButton(selected = option.value == selected, onClick = null) },
                onClick = {
                    onSelect(option.value)
                    expanded = false
                }
            )
        }
    }
}

/** One selectable option in a [SortMenu], carrying its enum value and a display label. */
data class SortOption<T>(val value: T, val label: String)
