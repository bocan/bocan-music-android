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

/** Immutable UI state for the sync status screen. */
data class SyncStatusUiState(
    val paired: Boolean = false,
    val serverName: String? = null,
    val lastSyncAt: Instant? = null,
    val generation: Long = 0,
    val counts: DownloadCounts = DownloadCounts(0, 0, 0),
    val storageBytes: Long = 0,
    val sync: SyncState = SyncState.Idle,
    val autoSyncEnabled: Boolean = true,
    val chargingOnly: Boolean = false
)

/**
 * Drives the sync status screen. A plain class wired by AppGraph (manual DI): it
 * folds the engine's live [SyncState], the paired-server row, the download
 * tallies, storage use, and the user's auto-sync toggles into one immutable state,
 * and forwards events (Sync Now, toggles) to the injected actions.
 */
class SyncStatusViewModel(private val sources: Sources, private val actions: Actions, dispatchers: CoroutineDispatchers) {
    /** The reactive inputs the screen folds together. */
    class Sources(
        val syncState: StateFlow<SyncState>,
        val server: Flow<SyncServerEntity?>,
        val counts: Flow<DownloadCounts>,
        val autoSync: StateFlow<Boolean>,
        val chargingOnly: StateFlow<Boolean>,
        val storageBytes: suspend () -> Long
    )

    /** The events the screen forwards to the sync graph. */
    class Actions(
        val syncNow: () -> Unit,
        val cancel: () -> Unit,
        val setAutoSync: (Boolean) -> Unit,
        val setChargingOnly: (Boolean) -> Unit
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val storage = MutableStateFlow(0L)

    val state: StateFlow<SyncStatusUiState> = run {
        val serverAndCounts = combine(sources.server, sources.counts, storage) { s, c, bytes -> Triple(s, c, bytes) }
        val settings = combine(sources.autoSync, sources.chargingOnly) { auto, charging -> auto to charging }
        combine(sources.syncState, serverAndCounts, settings) { sync, (srv, cnt, bytes), (auto, charging) ->
            SyncStatusUiState(
                paired = srv != null,
                serverName = srv?.serverName,
                lastSyncAt = srv?.lastSyncAt,
                generation = srv?.lastAppliedGeneration ?: 0,
                counts = cnt,
                storageBytes = bytes,
                sync = sync,
                autoSyncEnabled = auto,
                chargingOnly = charging
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

    fun setAutoSync(enabled: Boolean) = actions.setAutoSync(enabled)

    fun setChargingOnly(enabled: Boolean) = actions.setChargingOnly(enabled)

    fun refreshStorage() {
        scope.launch { storage.value = sources.storageBytes() }
    }

    /** Cancel the view model scope; call from the host's onDestroy. */
    fun dispose() {
        scope.cancel()
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
