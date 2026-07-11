package io.cloudcauldron.bocan.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R

/**
 * Settings: the pairing and library-sync entry points, plus the Podcasts section (default
 * speed, skip intervals, and a note that episode resume is seeded from the Mac). Later
 * phases fill in EQ and scrobble settings here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPairing: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenScrobbling: () -> Unit,
    podcastSettings: PodcastSettingsViewModel,
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
            ListItem(
                headlineContent = { Text(stringResource(R.string.home_pair_action)) },
                leadingContent = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPairing)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.home_sync_action)) },
                leadingContent = { Icon(Icons.Rounded.Sync, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSync)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.eq_open)) },
                leadingContent = { Icon(Icons.Rounded.Equalizer, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenEqualizer)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.scrobble_open)) },
                leadingContent = { Icon(Icons.Rounded.Radio, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenScrobbling)
            )
            PodcastSettingsSection(podcastSettings)
        }
    }
}

@Composable
private fun PodcastSettingsSection(viewModel: PodcastSettingsViewModel) {
    val ui by viewModel.state.collectAsState()
    ListItem(
        headlineContent = { Text(stringResource(R.string.podcast_settings_title)) },
        leadingContent = { Icon(Icons.Rounded.Podcasts, contentDescription = null) }
    )
    SettingRow(stringResource(R.string.podcast_settings_default_speed)) {
        PodcastSettingsViewModel.SPEED_PRESETS.forEach { preset ->
            FilterChip(
                selected = ui.defaultSpeed == preset,
                onClick = { viewModel.setDefaultSpeed(preset) },
                label = { Text(stringResource(R.string.speed_value, preset.toFloat())) }
            )
        }
    }
    SettingRow(stringResource(R.string.podcast_settings_skip_back)) {
        PodcastSettingsViewModel.SKIP_PRESETS.forEach { preset ->
            FilterChip(
                selected = ui.skipBackSeconds == preset,
                onClick = { viewModel.setSkipBackSeconds(preset) },
                label = { Text(stringResource(R.string.podcast_settings_seconds, preset)) }
            )
        }
    }
    SettingRow(stringResource(R.string.podcast_settings_skip_forward)) {
        PodcastSettingsViewModel.SKIP_PRESETS.forEach { preset ->
            FilterChip(
                selected = ui.skipForwardSeconds == preset,
                onClick = { viewModel.setSkipForwardSeconds(preset) },
                label = { Text(stringResource(R.string.podcast_settings_seconds, preset)) }
            )
        }
    }
    Text(
        text = stringResource(R.string.podcast_settings_resume_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingRow(label: String, chips: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            chips()
        }
    }
}
