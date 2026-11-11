package io.cloudcauldron.bocan.app.pairing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.LocalNetworkPermissionNotice
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.userMessageRes
import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.discovery.DiscoveredMac
import io.cloudcauldron.bocan.sync.pairing.PairingState

/** Stateful entry point: binds a [PairingViewModel] to the stateless screen. */
@Composable
fun PairingScreen(viewModel: PairingViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.state.collectAsState()
    PairingScreen(
        uiState = uiState,
        onPair = viewModel::pair,
        onSubmitCode = viewModel::submitCode,
        onStartOver = viewModel::startOver,
        modifier = modifier
    )
}

/** Stateless pairing screen driven entirely by [uiState]. */
@Composable
fun PairingScreen(
    uiState: PairingUiState,
    onPair: (DiscoveredMac) -> Unit,
    onSubmitCode: (String) -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(R.string.pairing_title), style = MaterialTheme.typography.headlineMedium)
        when (val pairing = uiState.pairing) {
            PairingState.Discovering -> DiscoveryContent(uiState.pairableMacs, onPair)
            is PairingState.AwaitingCode -> CodeEntryContent(isError = false, onSubmitCode = onSubmitCode)
            is PairingState.Confirming -> BusyContent(stringResource(R.string.pairing_confirming))
            is PairingState.Paired -> PairedContent(pairing.serverName, onStartOver)
            is PairingState.Failed -> FailedContent(pairing.error, onSubmitCode, onStartOver)
        }
    }
}

@Composable
private fun DiscoveryContent(macs: List<DiscoveredMac>, onPair: (DiscoveredMac) -> Unit) {
    if (macs.isEmpty()) {
        Text(stringResource(R.string.pairing_searching), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.pairing_no_macs), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        LocalNetworkPermissionNotice()
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(macs, key = { it.fingerprint }) { mac ->
            MacRow(mac, onPair)
        }
    }
}

@Composable
private fun MacRow(mac: DiscoveredMac, onPair: (DiscoveredMac) -> Unit) {
    val rowDescription = stringResource(R.string.pairing_mac_row, mac.serviceName)
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(E2ETags.Pairing.MAC_ROW)
            .clickable { onPair(mac) }
            .semantics(mergeDescendants = true) { contentDescription = rowDescription }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(mac.serviceName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pairing_ready), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CodeEntryContent(isError: Boolean, onSubmitCode: (String) -> Unit) {
    Text(stringResource(R.string.pairing_enter_code_title), style = MaterialTheme.typography.titleLarge)
    if (isError) {
        Text(
            text = stringResource(R.string.pairing_code_mismatch),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
        )
    }
    Spacer(Modifier.height(8.dp))
    CodeEntryField(onCodeComplete = onSubmitCode, isError = isError, modifier = Modifier.testTag(E2ETags.Pairing.CODE))
}

@Composable
private fun BusyContent(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PairedContent(serverName: String, onStartOver: () -> Unit) {
    Text(stringResource(R.string.pairing_paired_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.pairing_paired_message, serverName), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
    Button(onClick = onStartOver, modifier = Modifier.testTag(E2ETags.Pairing.DONE)) {
        Text(stringResource(R.string.pairing_done))
    }
}

@Composable
private fun FailedContent(error: SyncError, onSubmitCode: (String) -> Unit, onStartOver: () -> Unit) {
    if (error is SyncError.CodeMismatch) {
        // Keyed on the mismatch (it counts the attempts left), so every wrong code gets a fresh,
        // empty field and a fresh live region that TalkBack announces again. Without the key a
        // second wrong code left the first's digits in place and said nothing new.
        key(error) { CodeEntryContent(isError = true, onSubmitCode = onSubmitCode) }
        return
    }
    Text(
        text = stringResource(error.userMessageRes()),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = onStartOver, modifier = Modifier.testTag(E2ETags.Pairing.START_OVER)) {
        Text(stringResource(R.string.pairing_start_over))
    }
}
