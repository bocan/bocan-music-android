package io.cloudcauldron.bocan.sync

import io.cloudcauldron.bocan.sync.engine.SyncState
import kotlinx.coroutines.flow.StateFlow

/**
 * The seam the platform entry points ([SyncForegroundService][io.cloudcauldron.bocan.sync.service.SyncForegroundService]
 * and [SyncWorker][io.cloudcauldron.bocan.sync.auto.SyncWorker]) use to reach the
 * app's single sync graph without :core:sync depending on :app.
 *
 * The `Application` in :app implements this interface, so a service or worker
 * recovers it with `applicationContext as SyncHost`. The engine, its scope, and
 * discovery all live behind this one narrow surface.
 */
interface SyncHost {
    /** The live sync state, mirrored from the engine, for the progress notification. */
    val syncState: StateFlow<SyncState>

    /** Start a sync in the application scope and return immediately. */
    fun launchSync(force: Boolean)

    /** Cooperatively stop the current sync (Cancel action, or a foreground-service timeout). */
    fun cancelSync()

    /**
     * Run one scheduled sync to completion: briefly wait for discovery to locate
     * the paired Mac, then sync if its generation moved. Used by the periodic
     * WorkManager worker, which is not tied to the app process being alive.
     */
    suspend fun runScheduledSync()
}
