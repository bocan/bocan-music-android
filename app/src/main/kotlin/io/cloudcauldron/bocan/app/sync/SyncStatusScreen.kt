package io.cloudcauldron.bocan.app.sync

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.engine.ItemFailure
import io.cloudcauldron.bocan.sync.engine.SyncState

/** Stateful entry point: binds a [SyncStatusViewModel] to the stateless screen. */
@Composable
fun SyncStatusScreen(viewModel: SyncStatusViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.state.collectAsState()
    SyncStatusScreen(
        uiState = uiState,
        onSyncNow = viewModel::syncNow,
        onCancel = viewModel::cancel,
        onSetAutoSync = viewModel::setAutoSync,
        onSetChargingOnly = viewModel::setChargingOnly,
        modifier = modifier
    )
}

/** Stateless sync status screen driven entirely by [uiState]. */
@Composable
fun SyncStatusScreen(
    uiState: SyncStatusUiState,
    onSyncNow: () -> Unit,
    onCancel: () -> Unit,
    onSetAutoSync: (Boolean) -> Unit,
    onSetChargingOnly: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.sync_status_title), style = MaterialTheme.typography.headlineMedium)
        if (!uiState.paired) {
            Text(stringResource(R.string.sync_not_paired), style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        uiState.serverName?.let { name ->
            Text(stringResource(R.string.sync_server_label, name), style = MaterialTheme.typography.titleMedium)
        }
        Text(statusLine(uiState.sync), style = MaterialTheme.typography.bodyLarge)
        LastSyncedText(uiState)
        Text(stringResource(R.string.sync_generation, uiState.generation), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.sync_counts, uiState.counts.downloaded, uiState.counts.pending, uiState.counts.failed),
            style = MaterialTheme.typography.bodyMedium
        )
        StorageText(uiState.storageBytes)
        Failures(uiState.sync)
        Spacer(Modifier.height(8.dp))
        SyncActionButton(uiState.sync, onSyncNow, onCancel)
        Toggles(uiState, onSetAutoSync, onSetChargingOnly)
    }
}

@Composable
private fun LastSyncedText(uiState: SyncStatusUiState) {
    val lastSync = uiState.lastSyncAt
    val text = if (lastSync == null) {
        stringResource(R.string.sync_never_synced)
    } else {
        val relative = DateUtils.getRelativeTimeSpanString(lastSync.toEpochMilli()).toString()
        stringResource(R.string.sync_last_synced, relative)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun StorageText(bytes: Long) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.sync_storage_used, Formatter.formatShortFileSize(context, bytes)),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun Failures(state: SyncState) {
    val failures: List<ItemFailure> = (state as? SyncState.Done)?.failures.orEmpty()
    if (failures.isEmpty()) return
    Text(stringResource(R.string.sync_failures_title), style = MaterialTheme.typography.titleSmall)
    failures.forEach { failure ->
        Text(
            stringResource(R.string.sync_failure_row, failure.id, failure.reason),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun SyncActionButton(state: SyncState, onSyncNow: () -> Unit, onCancel: () -> Unit) {
    val active = state is SyncState.CheckingManifest || state is SyncState.Transferring || state is SyncState.Applying
    if (active) {
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.sync_cancel_action)) }
    } else {
        Button(onClick = onSyncNow) { Text(stringResource(R.string.sync_now_action)) }
    }
}

@Composable
private fun Toggles(uiState: SyncStatusUiState, onSetAutoSync: (Boolean) -> Unit, onSetChargingOnly: (Boolean) -> Unit) {
    ToggleRow(stringResource(R.string.sync_auto_label), uiState.autoSyncEnabled, onSetAutoSync)
    ToggleRow(stringResource(R.string.sync_charging_label), uiState.chargingOnly, onSetChargingOnly)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun statusLine(state: SyncState): String = when (state) {
    is SyncState.Idle -> stringResource(R.string.sync_state_idle)
    is SyncState.CheckingManifest -> stringResource(R.string.sync_state_checking)
    is SyncState.Transferring -> stringResource(R.string.sync_state_transferring, state.filesDone, state.filesTotal)
    is SyncState.Applying -> stringResource(R.string.sync_state_applying)
    is SyncState.Done -> stringResource(R.string.sync_state_done)
    is SyncState.ServerUnreachable -> stringResource(R.string.sync_state_unreachable)
    is SyncState.Failed -> when (state.error) {
        is SyncError.InsufficientStorage -> stringResource(R.string.sync_state_failed_space)
        is SyncError.NotPaired -> stringResource(R.string.sync_state_failed_not_paired)
        else -> stringResource(R.string.sync_state_failed)
    }
}
