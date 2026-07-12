package io.cloudcauldron.bocan.app.sync

import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.model.DownloadCounts
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.engine.SyncState
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Immutable UI state for the sync settings and status surface. */
data class SyncStatusUiState(
    val paired: Boolean = false,
    val serverName: String? = null,
    val fingerprintTail: String? = null,
    val lastSyncAt: Instant? = null,
    val generation: Long = 0,
    val counts: DownloadCounts = DownloadCounts(0, 0, 0),
    val storageBytes: Long = 0,
    val sync: SyncState = SyncState.Idle,
    val syncOnDiscovery: Boolean = true,
    val periodicSync: Boolean = true,
    val chargingOnly: Boolean = false,
    val removingMedia: Boolean = false
)

/**
 * Drives the sync settings and status surface. A plain class wired by AppGraph
 * (manual DI): it folds the engine's live [SyncState], the paired-server row, the
 * download tallies, storage use, and the user's auto-sync toggles into one
 * immutable state, and forwards events (Sync Now, toggles, unpair, remove media)
 * to the injected actions.
 */
class SyncStatusViewModel(private val sources: Sources, private val actions: Actions, dispatchers: CoroutineDispatchers) {
    /** The three auto-sync toggle flows, grouped because they always travel together. */
    class ToggleFlows(val syncOnDiscovery: StateFlow<Boolean>, val periodicSync: StateFlow<Boolean>, val chargingOnly: StateFlow<Boolean>)

    /** The reactive inputs the screen folds together. */
    class Sources(
        val syncState: StateFlow<SyncState>,
        val server: Flow<SyncServerEntity?>,
        val counts: Flow<DownloadCounts>,
        val toggles: ToggleFlows,
        val storageBytes: suspend () -> Long
    )

    /** The three auto-sync toggle setters, grouped to mirror [ToggleFlows]. */
    class ToggleActions(
        val setSyncOnDiscovery: (Boolean) -> Unit,
        val setPeriodicSync: (Boolean) -> Unit,
        val setChargingOnly: (Boolean) -> Unit
    )

    /** The events the screen forwards to the sync graph. */
    class Actions(
        val syncNow: () -> Unit,
        val cancel: () -> Unit,
        val toggles: ToggleActions,
        val unpair: () -> Unit,
        val removeAllMedia: suspend () -> Unit
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val storage = MutableStateFlow(0L)
    private val removing = MutableStateFlow(false)

    val state: StateFlow<SyncStatusUiState> = run {
        val serverAndCounts = combine(sources.server, sources.counts, storage) { s, c, bytes -> Triple(s, c, bytes) }
        val toggles = combine(
            sources.toggles.syncOnDiscovery,
            sources.toggles.periodicSync,
            sources.toggles.chargingOnly
        ) { d, p, c -> Triple(d, p, c) }
        combine(sources.syncState, serverAndCounts, toggles, removing) { sync, (srv, cnt, bytes), (disc, periodic, charging), busy ->
            SyncStatusUiState(
                paired = srv != null,
                serverName = srv?.serverName,
                fingerprintTail = srv?.certFingerprint?.takeLast(FINGERPRINT_TAIL_CHARS),
                lastSyncAt = srv?.lastSyncAt,
                generation = srv?.lastAppliedGeneration ?: 0,
                counts = cnt,
                storageBytes = bytes,
                sync = sync,
                syncOnDiscovery = disc,
                periodicSync = periodic,
                chargingOnly = charging,
                removingMedia = busy
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), SyncStatusUiState())
    }

    init {
        refreshStorage()
        // Recompute storage each time a run reaches a terminal state, so the number reflects reality.
        scope.launch {
            sources.syncState
                .map { it is SyncState.Done }
                .distinctUntilChanged()
                .onEach { done -> if (done) refreshStorage() }
                .collect {}
        }
    }

    fun syncNow() = actions.syncNow()

    fun cancel() = actions.cancel()

    fun setSyncOnDiscovery(enabled: Boolean) = actions.toggles.setSyncOnDiscovery(enabled)

    fun setPeriodicSync(enabled: Boolean) = actions.toggles.setPeriodicSync(enabled)

    fun setChargingOnly(enabled: Boolean) = actions.toggles.setChargingOnly(enabled)

    fun unpair() = actions.unpair()

    /** Run the confirmed removal, holding a busy flag so the button cannot double-fire. */
    fun removeAllMedia() {
        if (removing.value) return
        removing.value = true
        scope.launch {
            try {
                actions.removeAllMedia()
            } finally {
                removing.value = false
                refreshStorage()
            }
        }
    }

    fun refreshStorage() {
        scope.launch { storage.value = sources.storageBytes() }
    }

    /** Cancel the view model scope; call from the host's onDestroy. */
    fun dispose() {
        scope.cancel()
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        const val FINGERPRINT_TAIL_CHARS = 8
    }
}
