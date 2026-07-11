package io.cloudcauldron.bocan.sync.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import io.cloudcauldron.bocan.sync.R
import io.cloudcauldron.bocan.sync.engine.SyncState

/**
 * Builds the sync progress notification. Indeterminate while the manifest is
 * checked or applied, determinate during transfer. Kept separate from the service
 * so the (platform-only) service body stays thin.
 */
internal class SyncNotifications(private val context: Context) {
    private val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sync_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    fun build(state: SyncState, cancelIntent: PendingIntent): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.sync_notification_title))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.sync_notification_cancel),
                    cancelIntent
                ).build()
            )
        when (state) {
            is SyncState.Transferring ->
                builder
                    .setContentText(context.getString(R.string.sync_notification_transferring, state.filesDone, state.filesTotal))
                    .setProgress(state.filesTotal.coerceAtLeast(1), state.filesDone, false)
            is SyncState.Applying ->
                builder
                    .setContentText(context.getString(R.string.sync_notification_applying))
                    .setProgress(0, 0, true)
            else ->
                builder
                    .setContentText(context.getString(R.string.sync_notification_checking))
                    .setProgress(0, 0, true)
        }
        return builder.build()
    }

    /** Post an updated progress notification for an in-flight sync. */
    fun notifyUpdate(state: SyncState, cancelIntent: PendingIntent) {
        manager.notify(NOTIFICATION_ID, build(state, cancelIntent))
    }

    companion object {
        const val CHANNEL_ID = "bocan.sync"
        const val NOTIFICATION_ID = 4201
    }
}
