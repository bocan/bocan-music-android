package io.cloudcauldron.bocan.app.settings.sections

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.SettingsToggleRow
import io.cloudcauldron.bocan.app.sync.SyncStatusUiState
import io.cloudcauldron.bocan.app.sync.SyncStatusViewModel

/** Stateful entry point: binds a [SyncStatusViewModel] to the stateless screen. */
@Composable
fun SyncSettingsScreen(viewModel: SyncStatusViewModel, onPair: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val uiState by viewModel.state.collectAsState()
    SyncSettingsScreen(
        uiState = uiState,
        callbacks = SyncSettingsCallbacks(
            onSyncNow = viewModel::syncNow,
            onCancel = viewModel::cancel,
            onSetSyncOnDiscovery = viewModel::setSyncOnDiscovery,
            onSetPeriodicSync = viewModel::setPeriodicSync,
            onSetChargingOnly = viewModel::setChargingOnly,
            onUnpair = viewModel::unpair,
            onRemoveAllMedia = viewModel::removeAllMedia,
            onPair = onPair,
            onBack = onBack
        ),
        modifier = modifier
    )
}

/** Stateless sync settings and status surface driven entirely by [uiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(uiState: SyncStatusUiState, callbacks: SyncSettingsCallbacks, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_status_title)) },
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
                .padding(bottom = 24.dp)
        ) {
            if (!uiState.paired) {
                UnpairedContent(callbacks.onPair)
            } else {
                PairedContent(uiState, callbacks)
            }
        }
    }
}

@Composable
private fun UnpairedContent(onPair: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.sync_not_paired), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onPair) {
            Text(stringResource(R.string.home_pair_action))
        }
    }
}

@Composable
private fun PairedContent(uiState: SyncStatusUiState, callbacks: SyncSettingsCallbacks) {
    var confirmUnpair by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PairedMacBlock(uiState, onUnpairRequested = { confirmUnpair = true })
        Spacer(Modifier.height(8.dp))
        StatusBlock(uiState)
        SyncActionButton(uiState.sync, callbacks.onSyncNow, callbacks.onCancel)
        Failures(uiState.sync)
    }
    Spacer(Modifier.height(16.dp))
    SettingsToggleRow(
        label = stringResource(R.string.sync_on_discovery_label),
        summary = stringResource(R.string.sync_on_discovery_summary),
        checked = uiState.syncOnDiscovery,
        onCheckedChange = callbacks.onSetSyncOnDiscovery
    )
    SettingsToggleRow(
        label = stringResource(R.string.sync_periodic_label),
        summary = stringResource(R.string.sync_periodic_summary),
        checked = uiState.periodicSync,
        onCheckedChange = callbacks.onSetPeriodicSync
    )
    SettingsToggleRow(
        label = stringResource(R.string.sync_charging_label),
        checked = uiState.chargingOnly,
        onCheckedChange = callbacks.onSetChargingOnly,
        enabled = uiState.periodicSync
    )
    StorageBlock(uiState, onRemoveRequested = { confirmRemove = true })

    if (confirmUnpair) {
        UnpairDialog(
            serverName = uiState.serverName.orEmpty(),
            onConfirm = {
                confirmUnpair = false
                callbacks.onUnpair()
            },
            onDismiss = { confirmUnpair = false }
        )
    }
    if (confirmRemove) {
        RemoveMediaDialog(
            onConfirm = {
                confirmRemove = false
                callbacks.onRemoveAllMedia()
            },
            onDismiss = { confirmRemove = false }
        )
    }
}

@Composable
private fun PairedMacBlock(uiState: SyncStatusUiState, onUnpairRequested: () -> Unit) {
    Text(stringResource(R.string.sync_server_label, uiState.serverName.orEmpty()), style = MaterialTheme.typography.titleMedium)
    uiState.fingerprintTail?.let { tail ->
        Text(
            stringResource(R.string.sync_fingerprint_tail, tail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    TextButton(onClick = onUnpairRequested) {
        Text(stringResource(R.string.sync_unpair_action))
    }
}

@Composable
private fun StatusBlock(uiState: SyncStatusUiState) {
    // The visible line carries live counts; the announced text is the phase only,
    // so TalkBack hears each phase change once instead of every file tick.
    val phase = phaseAnnouncement(uiState.sync)
    Text(
        text = statusLine(uiState.sync),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = phase
        }
    )
    LastSyncedText(uiState)
    Text(stringResource(R.string.sync_generation, uiState.generation), style = MaterialTheme.typography.bodyMedium)
    val downloaded = pluralStringResource(R.plurals.sync_count_downloaded, uiState.counts.downloaded, uiState.counts.downloaded)
    val pendingCount = pluralStringResource(R.plurals.sync_count_pending, uiState.counts.pending, uiState.counts.pending)
    val failed = pluralStringResource(R.plurals.sync_count_failed, uiState.counts.failed, uiState.counts.failed)
    Text(
        stringResource(R.string.sync_counts_joined, downloaded, pendingCount, failed),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun StorageBlock(uiState: SyncStatusUiState, onRemoveRequested: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.sync_storage_used, Formatter.formatShortFileSize(context, uiState.storageBytes)),
            style = MaterialTheme.typography.bodyMedium
        )
        if (uiState.removingMedia) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp).padding(end = 8.dp))
                Text(stringResource(R.string.sync_remove_media_working), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            OutlinedButton(
                onClick = onRemoveRequested,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.sync_remove_media_action))
            }
        }
    }
}
