package io.cloudcauldron.bocan.app.effects

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import io.cloudcauldron.bocan.playback.audio.EqBands
import io.cloudcauldron.bocan.playback.audio.EqState
import io.cloudcauldron.bocan.playback.audio.ReplayGainMode
import kotlin.math.roundToInt

/**
 * The Equalizer and effects screen: a master switch, the preset row, the ten vertical
 * band sliders, bass boost, volume levelling (ReplayGain) with a preamp, skip silence,
 * and the honest "Fade between tracks" control. Every edit flows through the view model
 * to DataStore and reaches the effects chain within a buffer. Reached from the Now
 * Playing overflow and from Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(viewModel: EqualizerViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.eq_title)) },
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
                .padding(horizontal = 16.dp)
        ) {
            MasterSwitch(state.enabled, viewModel::setEnabled)
            SectionLabel(R.string.eq_preset)
            PresetPicker(
                presets = state.allPresets,
                activePresetId = state.activePresetId,
                onSelect = viewModel::selectPreset,
                onSave = viewModel::saveUserPreset,
                onDelete = viewModel::deleteUserPreset
            )
            if (state.activePresetId == null) {
                Text(
                    stringResource(R.string.eq_custom),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            SectionLabel(R.string.eq_bands)
            BandRow(state, viewModel::setBand)
            BassBoost(state.bassBoostDb, viewModel::setBassBoost)
            ReplayGainSection(state, viewModel::setReplayGainMode, viewModel::setPreamp)
            SkipSilenceToggle(state.skipSilence, viewModel::setSkipSilence)
            FadeControl(state.fadeSeconds, viewModel::setFadeSeconds)
        }
    }
}

@Composable
private fun MasterSwitch(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.eq_enable), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.eq_enable_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun BandRow(state: EqState, onBand: (Int, Double) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)
    ) {
        EqBands.centersHz.forEachIndexed { index, centerHz ->
            BandSlider(
                centerHz = centerHz,
                gainDb = state.bandGainsDb.getOrElse(index) { 0.0 },
                onGain = { onBand(index, it) }
            )
        }
    }
}

@Composable
private fun BassBoost(bassBoostDb: Double, onBass: (Double) -> Unit) {
    LabelledSlider(
        label = stringResource(R.string.eq_bass_boost),
        valueText = stringResource(R.string.eq_decibels, "%.1f".format(bassBoostDb)),
        value = bassBoostDb.toFloat(),
        onValueChange = { onBass(it.toDouble()) },
        range = EqState.BASS_MIN_DB.toFloat()..EqState.BASS_MAX_DB.toFloat(),
        steps = BASS_STEPS
    )
}

@Composable
private fun ReplayGainSection(state: EqState, onMode: (ReplayGainMode) -> Unit, onPreamp: (Double) -> Unit) {
    SectionLabel(R.string.eq_replaygain)
    Text(
        stringResource(R.string.eq_replaygain_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        ReplayGainChip(R.string.eq_replaygain_off, state.replayGainMode == ReplayGainMode.Off) { onMode(ReplayGainMode.Off) }
        ReplayGainChip(R.string.eq_replaygain_track, state.replayGainMode == ReplayGainMode.Track) { onMode(ReplayGainMode.Track) }
        ReplayGainChip(R.string.eq_replaygain_album, state.replayGainMode == ReplayGainMode.Album) { onMode(ReplayGainMode.Album) }
    }
    if (state.replayGainMode != ReplayGainMode.Off) {
        LabelledSlider(
            label = stringResource(R.string.eq_preamp),
            valueText = stringResource(R.string.eq_decibels, signed(state.preampDb)),
            value = state.preampDb.toFloat(),
            onValueChange = { onPreamp(it.toDouble()) },
            range = EqState.PREAMP_MIN_DB.toFloat()..EqState.PREAMP_MAX_DB.toFloat(),
            steps = PREAMP_STEPS
        )
    }
}

@Composable
private fun ReplayGainChip(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(labelRes)) })
}

@Composable
private fun SkipSilenceToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.eq_skip_silence), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.eq_skip_silence_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun FadeControl(fadeSeconds: Int, onFade: (Int) -> Unit) {
    val valueText = if (fadeSeconds == 0) {
        stringResource(R.string.eq_fade_off)
    } else {
        stringResource(R.string.eq_seconds, fadeSeconds)
    }
    SectionLabel(R.string.eq_fade)
    Text(
        stringResource(R.string.eq_fade_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LabelledSlider(
        label = stringResource(R.string.eq_fade),
        valueText = valueText,
        value = fadeSeconds.toFloat(),
        onValueChange = { onFade(it.roundToInt()) },
        range = EqState.FADE_MIN_SECONDS.toFloat()..EqState.FADE_MAX_SECONDS.toFloat(),
        steps = EqState.FADE_MAX_SECONDS - 1
    )
}

// Bass 0..9 dB and preamp -6..+6 dB, both on a 0.5 dB grid.
private const val BASS_STEPS = 17
private const val PREAMP_STEPS = 23
