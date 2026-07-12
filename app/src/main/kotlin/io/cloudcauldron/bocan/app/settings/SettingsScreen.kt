package io.cloudcauldron.bocan.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.SettingsNavRow

/** The settings hub: one row per section, each opening its own screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenSync: () -> Unit,
    onOpenPlayback: () -> Unit,
    onOpenPodcasts: () -> Unit,
    onOpenScrobbling: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsNavRow(
                label = stringResource(R.string.sync_status_title),
                summary = stringResource(R.string.settings_section_sync_summary),
                icon = Icons.Rounded.Sync,
                onClick = onOpenSync
            )
            SettingsNavRow(
                label = stringResource(R.string.playback_settings_title),
                summary = stringResource(R.string.settings_section_playback_summary),
                icon = Icons.Rounded.Tune,
                onClick = onOpenPlayback
            )
            SettingsNavRow(
                label = stringResource(R.string.podcast_settings_title),
                summary = stringResource(R.string.settings_section_podcasts_summary),
                icon = Icons.Rounded.Podcasts,
                onClick = onOpenPodcasts
            )
            SettingsNavRow(
                label = stringResource(R.string.scrobble_title),
                summary = stringResource(R.string.settings_section_scrobble_summary),
                icon = Icons.Rounded.Radio,
                onClick = onOpenScrobbling
            )
            SettingsNavRow(
                label = stringResource(R.string.appearance_title),
                summary = stringResource(R.string.settings_section_appearance_summary),
                icon = Icons.Rounded.Palette,
                onClick = onOpenAppearance
            )
            SettingsNavRow(
                label = stringResource(R.string.about_title),
                summary = stringResource(R.string.settings_section_about_summary),
                icon = Icons.Rounded.Info,
                onClick = onOpenAbout
            )
        }
    }
}
