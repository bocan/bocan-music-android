package io.cloudcauldron.bocan.sync.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The production [NsdServiceBrowser] over NsdManager. Discovery pushes found and
 * lost services; each found service is resolved through [ResolveQueue] one at a
 * time (the platform resolver rejects overlapping calls), and resolved services
 * are collected into a live snapshot emitted on the flow.
 *
 * Uses the pre-API-34 resolveService path so a single code path serves minSdk 29
 * through the latest release; the API-34 registerServiceInfoCallback replacement
 * is not available at minSdk. Excluded from the coverage floor: NsdManager needs
 * a real network stack and cannot be exercised in a JVM unit test.
 */
internal class NsdServiceBrowserImpl(context: Context, private val resolveQueue: ResolveQueue = ResolveQueue()) : NsdServiceBrowser {
    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val log = AppLog.forCategory(LogCategory.Network)

    override fun services(): Flow<List<ResolvedService>> = callbackFlow {
        val found = LinkedHashMap<String, ResolvedService>()
        val guard = Mutex()

        suspend fun mutateAndEmit(mutate: MutableMap<String, ResolvedService>.() -> Unit) {
            val snapshot = guard.withLock {
                found.mutate()
                found.values.toList()
            }
            trySend(snapshot)
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                launch {
                    resolveQueue.serialize {
                        val resolved = resolve(service) ?: return@serialize
                        mutateAndEmit { put(resolved.serviceName, resolved) }
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                launch { mutateAndEmit { remove(service.serviceName) } }
            }

            override fun onDiscoveryStarted(serviceType: String) {
                log.debug("discovery.started", mapOf("type" to serviceType))
            }

            override fun onDiscoveryStopped(serviceType: String) {
                log.debug("discovery.stopped", mapOf("type" to serviceType))
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("mDNS discovery failed to start: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                log.warning("discovery.stopFailed", mapOf("code" to errorCode))
            }
        }

        nsdManager.discoverServices(MacDiscovery.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
                .onFailure { log.warning("discovery.stopThrew", mapOf("error" to it.toString())) }
        }
    }

    @Suppress("DEPRECATION") // resolveService: the API-34 replacement needs minSdk 34; we support 29.
    private suspend fun resolve(info: NsdServiceInfo): ResolvedService? =
        suspendCancellableCoroutine { continuation ->
            nsdManager.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        continuation.resumeWith(Result.success(toResolvedService(resolved)))
                    }

                    override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                        log.warning("discovery.resolveFailed", mapOf("service" to failed.serviceName, "code" to errorCode))
                        continuation.resumeWith(Result.success(null))
                    }
                }
            )
        }

    @Suppress("DEPRECATION") // NsdServiceInfo.host: hostAddresses is API 34; host works from API 29.
    private fun toResolvedService(info: NsdServiceInfo): ResolvedService? {
        val host = info.host ?: return null
        val txt = info.attributes.mapValues { (_, value) -> value?.toString(Charsets.US_ASCII) }
        return ResolvedService(info.serviceName, host, info.port, txt)
    }
}
