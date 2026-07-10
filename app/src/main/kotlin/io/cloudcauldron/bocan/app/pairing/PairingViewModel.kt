package io.cloudcauldron.bocan.app.pairing

import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.discovery.DiscoveredMac
import io.cloudcauldron.bocan.sync.discovery.MacDiscovery
import io.cloudcauldron.bocan.sync.pairing.PairingClient
import io.cloudcauldron.bocan.sync.pairing.PairingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Immutable UI state for the pairing screen. */
data class PairingUiState(
    val pairableMacs: List<DiscoveredMac> = emptyList(),
    val pairing: PairingState = PairingState.Discovering,
    val busy: Boolean = false
)

/**
 * Drives the pairing screen: it surfaces the Macs currently in pairing mode and
 * the ceremony state, and forwards user events to [PairingClient]. A plain class
 * wired by AppGraph (manual DI); it owns a scope cancelled by [dispose].
 */
class PairingViewModel(private val discovery: MacDiscovery, private val pairingClient: PairingClient, dispatchers: CoroutineDispatchers) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<PairingUiState> = combine(
        discovery.discover().onStart { emit(emptyList()) },
        pairingClient.state,
        busy
    ) { macs, pairing, isBusy ->
        PairingUiState(
            pairableMacs = macs.filter { it.pairingMode },
            pairing = pairing,
            busy = isBusy
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), PairingUiState())

    fun pair(mac: DiscoveredMac) = launchBusy { pairingClient.start(mac) }

    fun submitCode(code: String) = launchBusy { pairingClient.submitCode(code) }

    fun startOver() {
        pairingClient.reset()
    }

    /** Cancel the view model scope; call from the host's onDestroy. */
    fun dispose() {
        scope.cancel()
    }

    private fun launchBusy(block: suspend () -> Unit) {
        scope.launch {
            busy.value = true
            try {
                block()
            } finally {
                busy.value = false
            }
        }
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
