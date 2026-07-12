package io.cloudcauldron.bocan.app.settings.sections

import android.text.format.DateUtils
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.sync.SyncStatusUiState
import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.engine.ItemFailure
import io.cloudcauldron.bocan.sync.engine.SyncState

@Composable
internal fun LastSyncedText(uiState: SyncStatusUiState) {
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
internal fun Failures(state: SyncState) {
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
internal fun SyncActionButton(state: SyncState, onSyncNow: () -> Unit, onCancel: () -> Unit) {
    val active = state is SyncState.CheckingManifest || state is SyncState.Transferring || state is SyncState.Applying
    if (active) {
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.sync_cancel_action)) }
    } else {
        Button(onClick = onSyncNow) { Text(stringResource(R.string.sync_now_action)) }
    }
}

@Composable
internal fun statusLine(state: SyncState): String = when (state) {
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

/** The phase-only text a screen reader announces: stable within a phase, no per-file ticks. */
@Composable
internal fun phaseAnnouncement(state: SyncState): String = when (state) {
    is SyncState.Transferring -> stringResource(R.string.sync_state_transferring_a11y)
    else -> statusLine(state)
}
