package io.cloudcauldron.bocan.app.fixtures

import io.cloudcauldron.bocan.sync.discovery.NsdServiceBrowser
import io.cloudcauldron.bocan.sync.discovery.ResolvedService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Debug builds only: lets the end-to-end suite stand in for mDNS (phase 17 slice 6). While
 * [advertise] holds a list, discovery sees exactly those adverts (the loopback fake Mac) and
 * the real NsdManager browse stops; after [clear], or when nothing is set, the real browse
 * runs as in any build. The adverts still go through the app's TXT parsing, so a malformed
 * advert is dropped exactly as a real one would be. The release source set's twin passes the
 * real browser through untouched.
 */
object DiscoveryOverride {
    private val adverts = MutableStateFlow<List<ResolvedService>?>(null)

    /** Replace mDNS with [services] until [clear]. */
    fun advertise(services: List<ResolvedService>) {
        adverts.value = services
    }

    /** Hand discovery back to the real mDNS browse. */
    fun clear() {
        adverts.value = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun wrap(real: NsdServiceBrowser): NsdServiceBrowser = object : NsdServiceBrowser {
        override fun services(): Flow<List<ResolvedService>> =
            adverts.flatMapLatest { injected -> injected?.let(::flowOf) ?: real.services() }
    }
}

/** The discovery browser this build uses: the real one, overridable by the end-to-end suite. */
internal fun discoveryBrowser(real: NsdServiceBrowser): NsdServiceBrowser = DiscoveryOverride.wrap(real)
