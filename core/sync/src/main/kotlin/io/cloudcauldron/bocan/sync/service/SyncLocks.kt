package io.cloudcauldron.bocan.sync.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * The CPU and Wi-Fi locks a sync holds for the duration of a transfer, so a large sync with
 * the screen off is not stalled by CPU suspend or Wi-Fi power save (which surfaced as
 * intermittent "Mac not reachable"). Both locks are non reference counted and the holder is
 * idempotent: a repeat [acquire] while already held is a no-op, and [release] is always safe
 * to call. FULL_LOW_LATENCY needs a foreground app or service, which the dataSync foreground
 * service satisfies.
 */
internal class SyncLocks(context: Context) {
    private val power = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun acquire() {
        if (wakeLock?.isHeld == true) return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
            setReferenceCounted(false)
            acquire(MAX_SYNC_DURATION_MS)
        }
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    fun release() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private companion object {
        const val TAG = "bocan:sync"

        // A backstop timeout on the wake lock so a wedged sync can never hold it forever; it
        // sits above the Android 15 six hour dataSync foreground cap, which stops the service
        // (releasing the lock) well before this fires.
        const val MAX_SYNC_DURATION_MS = 7L * 60 * 60 * 1000
    }
}
