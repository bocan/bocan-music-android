package io.cloudcauldron.bocan.sync.pairing

import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.discovery.DiscoveredMac

/**
 * The pairing ceremony as a state machine (sync-protocol.md section 4). The
 * [PairingClient] drives these transitions; the UI renders them.
 */
sealed interface PairingState {
    /** Browsing for Macs in pairing mode; nothing selected yet. */
    data object Discovering : PairingState

    /** pair/start succeeded; waiting for the user to type the code shown on the Mac. */
    data class AwaitingCode(val mac: DiscoveredMac, val expectedCode: String) : PairingState

    /** The typed code matched; sending pair/confirm and persisting trust. */
    data class Confirming(val mac: DiscoveredMac) : PairingState

    /** Paired: the pinned relationship is persisted. */
    data class Paired(val serverName: String) : PairingState

    /** The ceremony failed; [error] says why. */
    data class Failed(val error: SyncError) : PairingState
}
