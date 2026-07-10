package io.cloudcauldron.bocan.sync.discovery

import android.content.Context
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Turns the raw mDNS stream from [NsdServiceBrowser] into a live list of
 * [DiscoveredMac]s, parsing each TXT record and dropping anything malformed. A
 * Wi-Fi multicast lock is held for the lifetime of a collection so mDNS frames
 * keep arriving, and always released when collection ends or is cancelled.
 */
class MacDiscovery(
    private val browser: NsdServiceBrowser,
    private val multicastLease: WifiMulticastLease,
    private val dispatchers: CoroutineDispatchers
) {
    private val log = AppLog.forCategory(LogCategory.Pairing)

    /** Every Mac currently advertising on the LAN, ordered by service name. */
    fun discover(): Flow<List<DiscoveredMac>> = browser.services()
        .map { services -> services.mapNotNull(::parse).sortedBy { it.serviceName } }
        .onStart { multicastLease.acquire() }
        .onCompletion { multicastLease.release() }
        .flowOn(dispatchers.io)

    /** Parse one resolved service into a [DiscoveredMac], or null if it is not a valid advert. */
    internal fun parse(service: ResolvedService): DiscoveredMac? {
        val fingerprint = service.txt[KEY_FINGERPRINT]?.lowercase()?.takeIf { FINGERPRINT_FORMAT.matches(it) }
        val protocolVersion = service.txt[KEY_VERSION]?.toIntOrNull()
        if (fingerprint == null || protocolVersion == null) {
            val reason = if (fingerprint == null) "fingerprint" else "version"
            log.debug("discovery.dropped", mapOf("service" to service.serviceName, "reason" to reason))
            return null
        }
        return DiscoveredMac(
            serviceName = service.serviceName,
            host = service.host,
            port = service.port,
            fingerprint = fingerprint,
            pairingMode = service.txt[KEY_PAIRING_MODE] == PAIRING_MODE_ON,
            protocolVersion = protocolVersion
        )
    }

    companion object {
        const val SERVICE_TYPE = "_bocansync._tcp."

        /** Wire discovery to the real NsdManager and multicast lock for [context]. */
        fun create(context: Context, dispatchers: CoroutineDispatchers): MacDiscovery =
            MacDiscovery(NsdServiceBrowserImpl(context), WifiMulticastLeaseImpl(context), dispatchers)

        private const val KEY_VERSION = "v"
        private const val KEY_FINGERPRINT = "fp"
        private const val KEY_PAIRING_MODE = "pm"
        private const val PAIRING_MODE_ON = "1"
        private val FINGERPRINT_FORMAT = Regex("[0-9a-f]{64}")
    }
}
