package io.cloudcauldron.bocan.sync.engine

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.SyncApplier
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.model.manifest.Manifest
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.SyncError
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.HttpUrl

/** The paired-side ping and manifest surface, plus how to reach and stream the server. */
class SyncTransport(val endpoints: SyncEndpointProvider, val api: SyncApi, val transferrer: FileTransferrer)

/** The local persistence and filesystem the engine writes through. */
class SyncStore(val applier: SyncApplier, val mediaLayout: MediaLayout, val pendingDownloads: suspend () -> Int)

/** Test seams and tuning with production defaults. */
class SyncConfig(val now: () -> Instant = Instant::now, val freeSpaceBytes: (() -> Long?)? = null)

/**
 * The one-way sync orchestrator, implementing sync-protocol.md section 9 exactly
 * with one deliberate ordering choice: files are transferred BEFORE the database
 * is applied, so the library never references audio that is not yet on disk.
 *
 * A run is: ping -> generation short-circuit -> manifest -> [SyncApplier.plan]
 * (read only) -> transfer queue (artwork, then tracks by album, then episodes,
 * three in parallel) -> [SyncApplier.apply] (one transaction) -> mark downloaded
 * files (batched so an interruption keeps its progress) -> delete departed files
 * -> prune empty directories.
 *
 * Interruptions are safe by construction: `.part` files resume with Range
 * requests, verified files land atomically, the database flips in a single
 * transaction, and a re-run converges. Items that fail to download stay pending
 * and are retried next sync.
 */
class SyncEngine(
    private val trustStore: PairedServer,
    private val transport: SyncTransport,
    private val store: SyncStore,
    private val dispatchers: CoroutineDispatchers,
    private val scope: CoroutineScope,
    private val config: SyncConfig = SyncConfig()
) {
    /** The narrow read the engine needs of the trust store, so tests can fake it. */
    fun interface PairedServer {
        suspend fun current(): SyncServerEntity?
    }

    private val log = AppLog.forCategory(LogCategory.Sync)
    private val stateFlow = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = stateFlow.asStateFlow()

    private val runLock = Mutex()

    @Volatile private var activeJob: Job? = null

    /**
     * Run a sync now. Overlapping calls coalesce: while one run holds the lock, a
     * second call returns immediately. [force] skips the generation short-circuit.
     */
    suspend fun syncNow(force: Boolean = false) {
        if (!runLock.tryLock()) {
            log.debug("sync.coalesced", emptyMap())
            return
        }
        val job = scope.launch(dispatchers.default) { runGuarded(force) }
        activeJob = job
        try {
            job.join()
        } finally {
            activeJob = null
            runLock.unlock()
        }
    }

    /** Stop the current run cooperatively; the next [syncNow] resumes from disk. */
    fun cancel() {
        activeJob?.cancel()
    }

    private suspend fun runGuarded(force: Boolean) {
        try {
            runSync(force)
        } catch (e: CancellationException) {
            log.info("sync.cancelled", mapOf("cause" to e.message))
            stateFlow.value = SyncState.Idle
        } catch (e: SyncError.Network) {
            log.warning("sync.serverUnreachable", mapOf("error" to e.toString()))
            stateFlow.value = SyncState.ServerUnreachable
        } catch (e: SyncError) {
            log.error("sync.failed", mapOf("error" to e.toString()))
            stateFlow.value = SyncState.Failed(e)
        }
    }

    private suspend fun runSync(force: Boolean) {
        val prep = prepare() ?: return
        stateFlow.value = SyncState.CheckingManifest
        val ping = transport.api.ping(prep.base)
        if (ping.protocolVersion > PROTOCOL_VERSION) throw SyncError.UnsupportedProtocol(ping.protocolVersion, PROTOCOL_VERSION)
        if (!force && ping.generation == prep.server.lastAppliedGeneration && store.pendingDownloads() == 0) {
            log.debug("sync.upToDate", mapOf("generation" to ping.generation))
            stateFlow.value = SyncState.Done(config.now(), downloaded = 0, deleted = 0, failures = emptyList())
            return
        }
        val manifest = transport.api.manifest(prep.base)
        validateManifest(manifest, prep.server)
        converge(manifest, prep.base)
    }

    /** Resolve the paired server and its current address, or set a terminal state and return null. */
    private suspend fun prepare(): Prep? {
        val server = trustStore.current()
        val base = if (server != null) transport.endpoints.current() else null
        return when {
            server == null -> {
                stateFlow.value = SyncState.Failed(SyncError.NotPaired)
                null
            }
            base == null -> {
                log.info("sync.noEndpoint", emptyMap())
                stateFlow.value = SyncState.ServerUnreachable
                null
            }
            else -> Prep(server, base)
        }
    }

    private suspend fun converge(initial: Manifest, base: HttpUrl) {
        var manifest = initial
        var staleRetries = 0
        while (true) {
            // The plan is a read-only diff taken BEFORE apply; its relPathsToDelete
            // must be captured here, because after apply the DB matches the manifest
            // and nothing would look departed anymore.
            val plan = store.applier.plan(manifest)
            val queue = transport.transferrer.buildQueue(plan)
            ensureSpace(queue.bytesTotal)
            stateFlow.value = SyncState.Transferring(0, queue.filesTotal, 0, queue.bytesTotal, "")
            val outcome = try {
                transport.transferrer.transfer(queue, base) { p ->
                    stateFlow.value = SyncState.Transferring(p.filesDone, p.filesTotal, p.bytesDone, p.bytesTotal, p.label)
                }
            } catch (stale: SyncError.ManifestStale) {
                staleRetries++
                if (staleRetries > MAX_STALE_RETRIES) throw stale
                log.info("sync.manifestStale", mapOf("url" to stale.url, "retry" to staleRetries))
                manifest = transport.api.manifest(base)
                continue
            }
            applyAndClean(manifest, plan.relPathsToDelete, outcome)
            return
        }
    }

    private suspend fun applyAndClean(manifest: Manifest, relPathsToDelete: List<String>, outcome: FileTransferrer.Outcome) {
        stateFlow.value = SyncState.Applying(PHASE_DATABASE)
        store.applier.apply(manifest)
        // Flip verified files to downloaded in batches, so an interrupted mark keeps prior progress.
        outcome.trackIds.chunked(MARK_BATCH).forEach { store.applier.markDownloaded(it, emptyList()) }
        outcome.episodeIds.chunked(MARK_BATCH).forEach { store.applier.markDownloaded(emptyList(), it) }
        stateFlow.value = SyncState.Applying(PHASE_CLEANUP)
        deleteDeparted(relPathsToDelete)
        store.mediaLayout.pruneEmptyDirs()
        log.info(
            "sync.done",
            mapOf("downloaded" to outcome.downloadedCount, "deleted" to relPathsToDelete.size, "failures" to outcome.failures.size)
        )
        stateFlow.value = SyncState.Done(config.now(), outcome.downloadedCount, relPathsToDelete.size, outcome.failures)
    }

    private fun ensureSpace(bytesTotal: Long) {
        val available = (config.freeSpaceBytes?.invoke() ?: store.mediaLayout.usableSpaceBytes()) ?: throw SyncError.MediaUnavailable
        if (available < bytesTotal) throw SyncError.InsufficientStorage(bytesTotal, available)
    }

    private fun deleteDeparted(relPaths: List<String>) {
        relPaths.forEach { relPath ->
            val file = try {
                store.mediaLayout.fileForRelPath(relPath)
            } catch (e: SyncError.UnsafePath) {
                // A locally stored path should already be safe; never let cleanup fail a committed sync.
                log.warning("sync.deleteSkipped", mapOf("error" to e.toString()))
                return@forEach
            }
            if (file.isFile && !file.delete()) {
                log.warning("sync.deleteFailed", mapOf("path" to relPath))
            }
        }
    }

    private fun validateManifest(manifest: Manifest, server: SyncServerEntity) {
        if (manifest.protocolVersion > PROTOCOL_VERSION) {
            throw SyncError.UnsupportedProtocol(manifest.protocolVersion, PROTOCOL_VERSION)
        }
        if (manifest.serverId != server.serverId) {
            throw SyncError.MalformedResponse("manifest serverId ${manifest.serverId} is not the paired ${server.serverId}")
        }
    }

    private data class Prep(val server: SyncServerEntity, val base: HttpUrl)

    private companion object {
        const val PROTOCOL_VERSION = 1
        const val MAX_STALE_RETRIES = 3
        const val MARK_BATCH = 50
        const val PHASE_DATABASE = "database"
        const val PHASE_CLEANUP = "cleanup"
    }
}
