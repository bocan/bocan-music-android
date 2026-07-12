package io.cloudcauldron.bocan.app.settings.sections

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.SettingsToggleRow
import io.cloudcauldron.bocan.app.data.AppearanceSettings
import io.cloudcauldron.bocan.app.data.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Appearance settings: theme mode (system, light, dark), dynamic Material You
 * color with the Bocan brand palette as its fallback, and pure-black dark for OLED.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(settings: Flow<AppearanceSettings>, callbacks: AppearanceCallbacks, modifier: Modifier = Modifier) {
    val current by settings.collectAsState(initial = AppearanceSettings())
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.appearance_theme_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ThemeModePicker(current.themeMode, callbacks.onSetThemeMode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsToggleRow(
                    label = stringResource(R.string.appearance_dynamic_label),
                    summary = stringResource(R.string.appearance_dynamic_summary),
                    checked = current.dynamicColor,
                    onCheckedChange = callbacks.onSetDynamicColor
                )
            }
            SettingsToggleRow(
                label = stringResource(R.string.appearance_pure_black_label),
                summary = stringResource(R.string.appearance_pure_black_summary),
                checked = current.pureBlack,
                onCheckedChange = callbacks.onSetPureBlack
            )
        }
    }
}

@Composable
private fun ThemeModePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        ThemeMode.entries.forEach { mode ->
            ThemeModeRow(mode, selected == mode, onSelect)
        }
    }
}

@Composable
private fun ThemeModeRow(mode: ThemeMode, selected: Boolean, onSelect: (ThemeMode) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(mode) })
            .padding(horizontal = 16.dp)
    ) {
        // The row owns the radio semantics; the button is purely visual here.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(mode.label()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.System -> R.string.appearance_theme_system
    ThemeMode.Light -> R.string.appearance_theme_light
    ThemeMode.Dark -> R.string.appearance_theme_dark
}
