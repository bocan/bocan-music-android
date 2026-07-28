package io.cloudcauldron.bocan.sync.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether this phone is on a network that could carry mDNS to the paired Mac.
 * A seam so tests never touch ConnectivityManager.
 */
interface NetworkAvailability {
    /** True while at least one local-network transport is up. Emits on every change. */
    fun online(): Flow<Boolean>
}

/**
 * The production [NetworkAvailability] over ConnectivityManager. Only Wi-Fi and
 * Ethernet count: sync is local-network only, so cellular data is not a network
 * the Mac can be reached on. Requires ACCESS_NETWORK_STATE (declared in this
 * module's manifest). Excluded from the coverage floor: it is platform glue with
 * no logic of its own, and ConnectivityManager needs a real network stack. The
 * restart behaviour it feeds is covered by [restartWhileOnline]'s tests.
 */
internal class ConnectivityNetworkAvailability(context: Context) : NetworkAvailability {
    private val connectivity =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val log = AppLog.forCategory(LogCategory.Network)

    override fun online(): Flow<Boolean> = callbackFlow {
        // Mutated only from the ConnectivityManager callback thread, which delivers
        // onAvailable and onLost serially, so it needs no further guarding.
        val up = mutableSetOf<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                up += network
                trySend(true)
            }

            override fun onLost(network: Network) {
                up -= network
                trySend(up.isNotEmpty())
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        // Seed offline so a collector starting with no network does not stall waiting for a
        // callback that will never come. Registration replays onAvailable for networks that
        // are already up, so an online phone flips to true immediately after this.
        trySend(false)
        connectivity.registerNetworkCallback(request, callback)

        awaitClose {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
                .onFailure { log.warning("network.unregisterThrew", mapOf("error" to it.toString())) }
        }
    }.distinctUntilChanged()
}
