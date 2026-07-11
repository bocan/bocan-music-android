package io.cloudcauldron.bocan.sync.auto

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.sync.SyncHost

/**
 * The periodic background sync (six-hourly, unmetered, optionally charging). It
 * delegates to the app's [SyncHost], which locates the paired Mac through a short
 * discovery window and syncs only if the generation moved. Doze means this will
 * not fire exactly on schedule; that is fine, discovery-driven sync is the primary
 * path (sync-protocol.md phase 03 gotchas).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val log = AppLog.forCategory(LogCategory.Sync)

    override suspend fun doWork(): Result {
        val host = applicationContext as? SyncHost ?: return Result.failure()
        log.info("sync.worker.start", emptyMap())
        host.runScheduledSync()
        log.info("sync.worker.end", emptyMap())
        return Result.success()
    }
}
