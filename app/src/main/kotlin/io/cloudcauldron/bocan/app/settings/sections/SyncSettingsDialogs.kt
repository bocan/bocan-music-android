package io.cloudcauldron.bocan.app.settings.sections

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags

/** Confirms forgetting the paired Mac; media and history stay on the phone. */
@Composable
internal fun UnpairDialog(serverName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_unpair_confirm_title, serverName)) },
        text = { Text(stringResource(R.string.sync_unpair_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(E2ETags.Sync.UNPAIR_CONFIRM)) {
                Text(stringResource(R.string.sync_unpair_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(E2ETags.Sync.UNPAIR_CANCEL)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** The scary confirm: deletes phone copies only, keeps pairing and history, and says so. */
@Composable
internal fun RemoveMediaDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_remove_media_confirm_title)) },
        text = { Text(stringResource(R.string.sync_remove_media_confirm_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag(E2ETags.Sync.REMOVE_MEDIA_CONFIRM)
            ) {
                Text(stringResource(R.string.sync_remove_media_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(E2ETags.Sync.REMOVE_MEDIA_CANCEL)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
