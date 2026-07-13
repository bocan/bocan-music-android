package io.cloudcauldron.bocan.app.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.pairing.PairingScreen
import io.cloudcauldron.bocan.app.pairing.PairingViewModel
import io.cloudcauldron.bocan.app.settings.sections.statusLine
import io.cloudcauldron.bocan.sync.engine.SyncState
import io.cloudcauldron.bocan.sync.pairing.PairingState
import kotlinx.coroutines.flow.StateFlow

/** The three onboarding steps, in order. */
private enum class OnboardingStep { Welcome, Pair, FirstSync }

/**
 * First-run flow: what Bocan is, pair with your Mac (the one pairing
 * implementation, embedded, never forked), then first-sync progress with
 * cancel-and-do-later. Every step is skippable and the flow is re-enterable
 * from Settings, About.
 */
@Composable
fun OnboardingFlow(
    pairingViewModelFactory: () -> PairingViewModel,
    syncState: StateFlow<SyncState>,
    onStartSync: () -> Unit,
    onCancelSync: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(OnboardingStep.Welcome) }
    // Onboarding runs edge-to-edge with no home shell, so it must own its system-bar insets
    // here or the welcome title slides under the status bar and the buttons under the nav bar.
    // The Surface stays full-bleed (background draws behind the bars); only the content insets.
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = step,
            label = "onboarding",
            modifier = Modifier.fillMaxSize().systemBarsPadding()
        ) { current ->
            when (current) {
                OnboardingStep.Welcome -> WelcomePage(
                    onGetStarted = { step = OnboardingStep.Pair },
                    onSkip = onFinished
                )
                OnboardingStep.Pair -> PairPage(
                    pairingViewModelFactory = pairingViewModelFactory,
                    onPaired = {
                        onStartSync()
                        step = OnboardingStep.FirstSync
                    },
                    onSkip = onFinished
                )
                OnboardingStep.FirstSync -> FirstSyncPage(
                    syncState = syncState,
                    onDone = onFinished,
                    onCancelAndLater = {
                        onCancelSync()
                        onFinished()
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(onGetStarted: () -> Unit, onSkip: () -> Unit) {
    OnboardingPage(
        title = stringResource(R.string.onboarding_welcome_title),
        primaryLabel = stringResource(R.string.onboarding_get_started),
        onPrimary = onGetStarted,
        secondaryLabel = stringResource(R.string.onboarding_skip),
        onSecondary = onSkip
    ) {
        Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PairPage(pairingViewModelFactory: () -> PairingViewModel, onPaired: () -> Unit, onSkip: () -> Unit) {
    val viewModel = remember { pairingViewModelFactory() }
    DisposableEffect(Unit) { onDispose { viewModel.dispose() } }
    val uiState by viewModel.state.collectAsState()
    LaunchedEffect(uiState.pairing) {
        if (uiState.pairing is PairingState.Paired) onPaired()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        PairingScreen(viewModel, modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}

@Composable
private fun FirstSyncPage(syncState: StateFlow<SyncState>, onDone: () -> Unit, onCancelAndLater: () -> Unit) {
    val state by syncState.collectAsState()
    OnboardingPage(
        title = stringResource(R.string.onboarding_sync_title),
        primaryLabel = stringResource(R.string.onboarding_done),
        onPrimary = onDone,
        secondaryLabel = stringResource(R.string.onboarding_sync_later),
        onSecondary = onCancelAndLater
    ) {
        Text(stringResource(R.string.onboarding_sync_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.padding(top = 16.dp))
        Text(statusLine(state), style = MaterialTheme.typography.bodyMedium)
        val transferring = state as? SyncState.Transferring
        if (transferring != null && transferring.filesTotal > 0) {
            LinearProgressIndicator(
                progress = { transferring.filesDone.toFloat() / transferring.filesTotal },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun OnboardingPage(
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            content()
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                Text(primaryLabel)
            }
            OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                Text(secondaryLabel)
            }
        }
    }
}
