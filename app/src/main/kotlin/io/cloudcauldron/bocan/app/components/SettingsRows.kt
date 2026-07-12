package io.cloudcauldron.bocan.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A settings toggle where the whole row is one Switch-role target: TalkBack
 * announces label, summary, and state as a single sentence, and the touch target
 * is the full row, never the 32 dp thumb.
 */
@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MIN_ROW_HEIGHT)
            .toggleable(value = checked, role = Role.Switch, enabled = enabled, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // The row owns the toggle semantics; the Switch is purely visual here.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A navigation row for the settings hub: icon, label, optional summary, chevron-free. */
@Composable
fun SettingsNavRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, summary: String? = null, icon: ImageVector? = null) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MIN_ROW_HEIGHT)
            .clickable(onClick = onClick)
    )
}

private val MIN_ROW_HEIGHT = 56.dp
