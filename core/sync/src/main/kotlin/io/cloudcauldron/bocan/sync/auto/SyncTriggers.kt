package io.cloudcauldron.bocan.sync.auto

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.sync.discovery.DiscoveredMac
import io.cloudcauldron.bocan.sync.discovery.DiscoveryBackoff
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.retryWhen
import okhttp3.HttpUrl

/**
 * The discovery-driven auto-sync trigger. While the app process is alive it
 * watches the mDNS stream, and whenever the paired Mac appears it publishes the
 * Mac's current address (for [io.cloudcauldron.bocan.sync.engine.SyncEndpointProvider])
 * and fires [onPairedVisible], debounced to at most once per [debounce] so a Mac
 * flapping in and out of the browse list does not start a sync every second.
 *
 * The generation check that decides whether there is actually anything to sync
 * lives in the engine's ping short-circuit; this class only decides when it is
 * worth asking.
 */
class SyncTriggers(
    private val discovery: Flow<List<DiscoveredMac>>,
    private val pairedFingerprint: suspend () -> String?,
    private val endpoint: MutableStateFlow<HttpUrl?>,
    private val onPairedVisible: suspend () -> Unit,
    private val debounce: Duration = DEFAULT_DEBOUNCE,
    private val clock: () -> Instant = Instant::now
) {
    private val log = AppLog.forCategory(LogCategory.Sync)
    private var lastTrigger: Instant? = null

    /**
     * Collect discovery forever (until the surrounding scope is cancelled). A failing
     * discovery stream is restarted rather than allowed to end the collection: this runs
     * in an application-scoped coroutine with no exception handler, so letting it throw
     * would both stop auto-sync for the life of the process and reach the default
     * uncaught handler.
     */
    suspend fun observe() {
        discovery
            .retryWhen { cause, attempt ->
                val wait = DiscoveryBackoff.delayMillis(attempt)
                log.warning(
                    "sync.discoveryRestarting",
                    mapOf("attempt" to attempt, "waitMs" to wait, "error" to cause.toString())
                )
                // The last known address came from a stream that has now failed; drop it so
                // a sync waits for a fresh sighting instead of dialling a stale host.
                endpoint.value = null
                delay(wait)
                true
            }
            .collect { macs -> onDiscovery(macs) }
    }

    /** Exposed for tests: process one discovery emission. */
    suspend fun onDiscovery(macs: List<DiscoveredMac>) {
        val fingerprint = pairedFingerprint()
        val mac = fingerprint?.let { fp -> macs.firstOrNull { it.fingerprint == fp } }
        endpoint.value = mac?.let(::endpointUrl)
        if (mac != null && !debounced()) {
            lastTrigger = clock()
            log.info("sync.triggerVisible", mapOf("server" to mac.serviceName))
            onPairedVisible()
        }
    }

    private fun debounced(): Boolean {
        val previous = lastTrigger ?: return false
        val elapsed = Duration.between(previous, clock())
        val within = elapsed < debounce
        if (within) log.debug("sync.triggerDebounced", mapOf("sinceMs" to elapsed.toMillis()))
        return within
    }

    private fun endpointUrl(mac: DiscoveredMac): HttpUrl = endpointOf(mac)

    private companion object {
        val DEFAULT_DEBOUNCE: Duration = Duration.ofMinutes(15)
    }
}

/** The base HTTPS URL of a discovered Mac. Identity is the pinned cert, not the host. */
fun endpointOf(mac: DiscoveredMac): HttpUrl = HttpUrl.Builder()
    .scheme("https")
    .host(mac.host.hostAddress ?: "127.0.0.1")
    .port(mac.port)
    .build()
