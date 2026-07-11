package io.cloudcauldron.bocan.sync.auto

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules and cancels the periodic sync worker. The constraints encode the
 * v1 policy: unmetered network always (the Mac is on the LAN), charging when the
 * user opts in. No exact alarms: Doze may defer the run and that is acceptable.
 */
class SyncWorkScheduler(private val context: Context) {
    /** (Re)schedule the six-hourly worker, honouring the charging toggle. */
    fun schedulePeriodic(requireCharging: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(requireCharging)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Turn auto-sync off: cancel the periodic worker. */
    fun cancelPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    private companion object {
        const val SYNC_INTERVAL_HOURS = 6L
        const val UNIQUE_NAME = "bocan.sync.periodic"
        const val TAG = "bocan-sync"
    }
}
