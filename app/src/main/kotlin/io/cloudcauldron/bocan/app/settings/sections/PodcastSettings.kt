package io.cloudcauldron.bocan.app.settings.sections

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.cloudcauldron.bocan.app.settings.PodcastSettingsViewModel

/** Podcast defaults: playback speed and the skip-back and skip-forward intervals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastSettingsScreen(viewModel: PodcastSettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsState()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.podcast_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            ChipRow(stringResource(R.string.podcast_settings_default_speed)) {
                PodcastSettingsViewModel.SPEED_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = ui.defaultSpeed == preset,
                        onClick = { viewModel.setDefaultSpeed(preset) },
                        label = { Text(stringResource(R.string.speed_value, preset.toFloat())) }
                    )
                }
            }
            ChipRow(stringResource(R.string.podcast_settings_skip_back)) {
                PodcastSettingsViewModel.SKIP_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = ui.skipBackSeconds == preset,
                        onClick = { viewModel.setSkipBackSeconds(preset) },
                        label = { Text(stringResource(R.string.podcast_settings_seconds, preset)) }
                    )
                }
            }
            ChipRow(stringResource(R.string.podcast_settings_skip_forward)) {
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(label: String, chips: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            chips()
        }
    }
}
