package io.cloudcauldron.bocan.sync.discovery

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retryWhen

/**
 * Keeps a browse session alive for as long as the phone is on a local network.
 *
 * mDNS discovery dies in two ways that were both permanent before this: NsdManager
 * can refuse to start a browse at all (Wi-Fi off, or still coming up), and a browse
 * registration does not survive the network going away and coming back. Either one
 * used to end the stream for the life of the process, so a phone that opened the app
 * off Wi-Fi never found the Mac again without a restart.
 *
 * Both are handled here. The session is torn down and started fresh whenever [online]
 * flips, and a session that fails is retried with [backoff]. Collectors see an empty
 * list whenever there is nothing to browse, and never see the failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<List<ResolvedService>>.restartWhileOnline(
    online: Flow<Boolean>,
    backoff: (Long) -> Long = DiscoveryBackoff::delayMillis
): Flow<List<ResolvedService>> {
    val log = AppLog.forCategory(LogCategory.Network)
    return online.distinctUntilChanged().flatMapLatest { isOnline ->
        if (!isOnline) {
            log.debug("discovery.offline", emptyMap())
            flowOf(emptyList())
        } else {
            retryWhen { cause, attempt ->
                val wait = backoff(attempt)
                log.warning(
                    "discovery.restarting",
                    mapOf("attempt" to attempt, "waitMs" to wait, "error" to cause.toString())
                )
                emit(emptyList())
                delay(wait)
                true
            }
        }
    }
}
