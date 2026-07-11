package io.cloudcauldron.bocan.sync.engine

import io.cloudcauldron.bocan.sync.SyncError
import java.time.Instant

/**
 * The UI-consumable state of the sync pipeline (design-spec phase 03). The engine
 * exposes this as a StateFlow so screens render progress without knowing anything
 * about HTTP, files, or the database.
 */
sealed interface SyncState {
    /** No sync in progress. */
    data object Idle : SyncState

    /** Talking to the server: ping and manifest fetch (progress is indeterminate). */
    data object CheckingManifest : SyncState

    /** Streaming files. Byte totals cover tracks and episodes; artwork is counted per file. */
    data class Transferring(val filesDone: Int, val filesTotal: Int, val bytesDone: Long, val bytesTotal: Long, val currentItem: String) :
        SyncState

    /** Applying the manifest to the database, then cleaning up departed files. */
    data class Applying(val phase: String) : SyncState

    /** The sync converged. [failures] lists per-item download failures that did not fail the whole run. */
    data class Done(val at: Instant, val downloaded: Int, val deleted: Int, val failures: List<ItemFailure>) : SyncState

    /** The sync failed outright with a typed error. */
    data class Failed(val error: SyncError) : SyncState

    /** The server went away mid-transfer; the queue is paused and resumes on rediscovery. */
    data object ServerUnreachable : SyncState
}

/** One track, episode, or artwork item that could not be downloaded this run. */
data class ItemFailure(val id: String, val kind: String, val reason: String)
