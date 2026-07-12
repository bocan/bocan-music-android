package io.cloudcauldron.bocan.app.settings

import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.scrobble.providers.ProviderId
import kotlinx.coroutines.launch

/**
 * Scrobble settings: the master switch, one row per provider (connect or disconnect, and
 * a per-provider enable toggle once connected), the queue depth, and the dead-letter list
 * with retry and discard. Last.fm connects through a browser auth flow; ListenBrainz and
 * Rocksky take a pasted token.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrobbleSettingsScreen(viewModel: ScrobbleSettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsState()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scrobble_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            MasterRow(ui.masterEnabled, viewModel::setMasterEnabled)
            ui.providers.forEach { provider -> ProviderRow(provider, viewModel) }
            val queueText = if (ui.queueDepth == 0) {
                stringResource(R.string.scrobble_queue_empty)
            } else {
                pluralStringResource(R.plurals.scrobble_queue_depth, ui.queueDepth, ui.queueDepth)
            }
            Text(
                text = queueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            if (ui.deadLettered.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.scrobble_dead_letter_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                ui.deadLettered.forEach { row -> DeadLetterItem(row, viewModel) }
            }
        }
    }
}

@Composable
private fun MasterRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.scrobble_master), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.scrobble_master_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun ProviderRow(provider: ScrobbleProviderRow, viewModel: ScrobbleSettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTokenDialog by remember { mutableStateOf(false) }
    var awaitingLastFm by remember { mutableStateOf(false) }

    HorizontalDivider()
    ProviderHeader(provider, viewModel)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        if (provider.connected) {
            OutlinedButton(onClick = { viewModel.disconnect(provider.id) }) { Text(stringResource(R.string.scrobble_disconnect)) }
        } else if (provider.id == ProviderId.LAST_FM) {
            OutlinedButton(onClick = {
                scope.launch {
                    val url = viewModel.beginLastFmAuth()
                    if (url != null) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        awaitingLastFm = true
                    }
                }
            }) { Text(stringResource(R.string.scrobble_connect)) }
            if (awaitingLastFm) {
                TextButton(onClick = {
                    viewModel.finishLastFmAuth()
                    awaitingLastFm = false
                }) { Text(stringResource(R.string.scrobble_lastfm_finish)) }
            }
        } else {
            OutlinedButton(onClick = { showTokenDialog = true }) { Text(stringResource(R.string.scrobble_connect)) }
        }
    }

    if (showTokenDialog) {
        TokenDialog(
            providerName = provider.displayName,
            onConfirm = { token ->
                viewModel.connectWithToken(provider.id, token)
                showTokenDialog = false
            },
            onDismiss = { showTokenDialog = false }
        )
    }
}

@Composable
private fun ProviderHeader(provider: ScrobbleProviderRow, viewModel: ScrobbleSettingsViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    provider.connected && provider.username != null -> stringResource(R.string.scrobble_connected, provider.username)
                    provider.connected -> stringResource(R.string.scrobble_connected_no_name)
                    else -> stringResource(R.string.scrobble_not_connected)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (provider.connected) {
            val switchLabel = stringResource(R.string.scrobble_enable_a11y, provider.displayName)
            Switch(
                checked = provider.enabled,
                onCheckedChange = { viewModel.setProviderEnabled(provider.id, it) },
                modifier = Modifier.semantics { contentDescription = switchLabel }
            )
        }
    }
}

@Composable
private fun DeadLetterItem(row: DeadLetterRow, viewModel: ScrobbleSettingsViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.scrobble_dead_letter_row, row.provider, row.title),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { viewModel.retryDeadLettered(row.id) }) { Text(stringResource(R.string.scrobble_retry)) }
        TextButton(onClick = { viewModel.discardDeadLettered(row.id) }) { Text(stringResource(R.string.scrobble_discard)) }
    }
}

@Composable
private fun TokenDialog(providerName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scrobble_token_title, providerName)) },
        text = {
            Column {
                Text(stringResource(R.string.scrobble_token_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.scrobble_token_hint)) },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(token) }, enabled = token.isNotBlank()) {
                Text(stringResource(R.string.scrobble_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.scrobble_cancel)) } }
    )
}
