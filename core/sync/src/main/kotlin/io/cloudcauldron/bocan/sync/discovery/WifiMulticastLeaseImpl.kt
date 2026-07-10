package io.cloudcauldron.bocan.sync.discovery

import android.content.Context
import android.net.wifi.WifiManager

/**
 * The production [WifiMulticastLease]: a reference-counted WifiManager multicast
 * lock. Requires CHANGE_WIFI_MULTICAST_STATE (declared in this module's
 * manifest). Excluded from the coverage floor: no behaviour to test off-device.
 */
internal class WifiMulticastLeaseImpl(context: Context) : WifiMulticastLease {
    private val lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock(TAG)
        .apply { setReferenceCounted(true) }

    override fun acquire() {
        lock.acquire()
    }

    override fun release() {
        if (lock.isHeld) {
            lock.release()
        }
    }

    private companion object {
        const val TAG = "bocan-mdns"
    }
}
