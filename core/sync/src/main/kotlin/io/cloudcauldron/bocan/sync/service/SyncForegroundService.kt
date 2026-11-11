package io.cloudcauldron.bocan.sync.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.sync.SyncHost
import io.cloudcauldron.bocan.sync.engine.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Keeps a sync alive while the user is away from the app, showing determinate
 * progress with a Cancel action. It is a thin shell: the engine does the work,
 * the service only promotes it to the foreground and mirrors [SyncState] into a
 * notification, stopping itself the moment the engine returns to a terminal state.
 *
 * Platform constraints (sync-protocol.md phase 03, binding):
 *  - dataSync foreground service type; on Android 15+ these are capped at six
 *    hours, so [onTimeout] pauses gracefully rather than crashing (the engine's
 *    resumability makes a pause a non-event).
 *  - Never started from BOOT_COMPLETED (disallowed for dataSync on Android 15+).
 *    The only starters are discovery, user action, and WorkManager.
 */
class SyncForegroundService : Service() {
    private val log = AppLog.forCategory(LogCategory.Sync)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: SyncNotifications
    private val host: SyncHost get() = application as SyncHost

    // Held for the duration of a sync so a large transfer with the screen off is not stalled
    // by CPU suspend or Wi-Fi power save (which surfaced as intermittent "Mac not reachable").
    // Released in onDestroy, which every stop path (the last run ending, cancel, timeout) reaches.
    private val locks by lazy { SyncLocks(this) }

    // Main thread only: every start and every run's end is handled on the main thread.
    private var runs = 0
    private var progress: Job? = null

    override fun onCreate() {
        super.onCreate()
        notifications = SyncNotifications(this)
        notifications.ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            log.info("sync.service.cancel", emptyMap())
            host.cancelSync()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            SyncNotifications.NOTIFICATION_ID,
            notifications.build(SyncState.CheckingManifest, cancelIntent()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        locks.acquire()
        if (progress == null) progress = observeProgress()
        // The service lives exactly as long as the runs it launched. It once stopped on the first
        // terminal state it saw, which was the Idle still current when it started: every sync ran
        // without its notification, its Cancel action, its locks, or a foreground service.
        runs++
        host.launchSync(intent?.getBooleanExtra(EXTRA_FORCE, false) ?: false)
            .invokeOnCompletion { scope.launch { onRunEnded() } }
        return START_NOT_STICKY
    }

    /** Mirror each in-flight state into the notification; terminal states are the runs' business. */
    private fun observeProgress(): Job = host.syncState
        .onEach { state -> if (!state.isTerminal) notifications.notifyUpdate(state, cancelIntent()) }
        .launchIn(scope)

    private fun onRunEnded() {
        runs--
        if (runs > 0) return
        log.debug("sync.service.stopping", mapOf("state" to host.syncState.value::class.simpleName))
        progress?.cancel()
        progress = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int) {
        pauseForTimeout()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        pauseForTimeout()
    }

    private fun pauseForTimeout() {
        log.warning("sync.service.timeout", emptyMap())
        host.cancelSync()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locks.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun cancelIntent(): PendingIntent {
        val intent = Intent(this, SyncForegroundService::class.java).setAction(ACTION_CANCEL)
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private val SyncState.isTerminal: Boolean
        get() = this is SyncState.Idle ||
            this is SyncState.Done ||
            this is SyncState.Failed ||
            this is SyncState.ServerUnreachable

    companion object {
        private const val ACTION_CANCEL = "io.cloudcauldron.bocan.sync.action.CANCEL"
        private const val EXTRA_FORCE = "force"

        /** Start a foreground sync. Callers must be an app-alive trigger, never BOOT_COMPLETED. */
        fun start(context: Context, force: Boolean) {
            val intent = Intent(context, SyncForegroundService::class.java).putExtra(EXTRA_FORCE, force)
            context.startForegroundService(intent)
        }
    }
}
