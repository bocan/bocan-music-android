package io.cloudcauldron.bocan.sync.discovery

import java.net.InetAddress
import kotlinx.coroutines.flow.Flow

/**
 * A resolved mDNS service, decoupled from NsdManager so the TXT-parsing and
 * discovery logic is testable without the Android network stack. TXT values are
 * decoded ASCII; a key present with no value is null.
 */
data class ResolvedService(val serviceName: String, val host: InetAddress, val port: Int, val txt: Map<String, String?>)

/**
 * Emits the current set of resolved `_bocansync._tcp` services. Implemented over
 * NsdManager in production and faked in tests.
 */
interface NsdServiceBrowser {
    fun services(): Flow<List<ResolvedService>>
}

/**
 * Holds a Wi-Fi multicast lock so mDNS frames keep arriving while discovery runs.
 * A seam so tests never touch WifiManager. Acquire and release must balance.
 */
interface WifiMulticastLease {
    fun acquire()

    fun release()
}
