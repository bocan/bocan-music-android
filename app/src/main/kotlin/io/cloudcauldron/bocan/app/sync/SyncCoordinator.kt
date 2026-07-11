package io.cloudcauldron.bocan.app.sync

import android.content.Context
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.persistence.SyncApplier
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.model.LyricsKind
import io.cloudcauldron.bocan.playback.lyrics.FetchResult
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.SyncHost
import io.cloudcauldron.bocan.sync.auto.SyncTriggers
import io.cloudcauldron.bocan.sync.auto.SyncWorkScheduler
import io.cloudcauldron.bocan.sync.auto.endpointOf
import io.cloudcauldron.bocan.sync.discovery.MacDiscovery
import io.cloudcauldron.bocan.sync.engine.ArtworkStore
import io.cloudcauldron.bocan.sync.engine.Downloader
import io.cloudcauldron.bocan.sync.engine.FileTransferrer
import io.cloudcauldron.bocan.sync.engine.MediaLayout
import io.cloudcauldron.bocan.sync.engine.SyncApi
import io.cloudcauldron.bocan.sync.engine.SyncEndpointProvider
import io.cloudcauldron.bocan.sync.engine.SyncEngine
import io.cloudcauldron.bocan.sync.engine.SyncState
import io.cloudcauldron.bocan.sync.engine.SyncStore
import io.cloudcauldron.bocan.sync.engine.SyncTransport
import io.cloudcauldron.bocan.sync.net.SyncHttpClientFactory
import io.cloudcauldron.bocan.sync.net.TrustStore
import io.cloudcauldron.bocan.sync.service.SyncForegroundService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl

/**
 * The app's single sync graph and the [SyncHost] the platform entry points reach.
 * It builds one [SyncEngine] per paired certificate (rebuilding only if the phone
 * re-pairs), keeps the paired Mac's live address in [endpoint] from discovery,
 * and translates host callbacks into engine calls. It owns an application-lifetime
 * scope, so a sync survives the UI going away.
 */
@Suppress("TooManyFunctions")
class SyncCoordinator(
    private val context: Context,
    private val dispatchers: CoroutineDispatchers,
    private val database: BocanDatabase,
    private val httpClientFactory: () -> SyncHttpClientFactory,
    private val discovery: MacDiscovery,
    private val settings: SyncSettings
) : SyncHost {
    private val log = AppLog.forCategory(LogCategory.Sync)
    private val appScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val endpoint = MutableStateFlow<HttpUrl?>(null)
    private val trustStore = TrustStore(database.syncDao())
    private val applier = SyncApplier(database)
    private val mediaLayout = MediaLayout(context)
    private val scheduler = SyncWorkScheduler(context)
    private val syncStateFlow = MutableStateFlow<SyncState>(SyncState.Idle)

    val artworkStore = ArtworkStore(mediaLayout)

    override val syncState: StateFlow<SyncState> = syncStateFlow.asStateFlow()

    @Volatile private var cached: Pair<String, SyncEngine>? = null

    private val triggers = SyncTriggers(
        discovery = discovery.discover(),
        pairedFingerprint = { trustStore.current()?.certFingerprint },
        endpoint = endpoint,
        onPairedVisible = { if (settings.autoSyncEnabled.value) SyncForegroundService.start(context, force = false) }
    )

    /** Start the always-on discovery trigger and schedule the periodic worker. */
    fun start() {
        appScope.launch { triggers.observe() }
        applyWorkerSchedule()
    }

    override fun launchSync(force: Boolean) {
        appScope.launch { engine()?.syncNow(force) }
    }

    override fun cancelSync() {
        cached?.second?.cancel()
    }

    override suspend fun runScheduledSync() {
        val engine = engine() ?: return
        // Give discovery up to 20 s to locate the paired Mac, then sync if needed.
        withTimeoutOrNull(DISCOVERY_WINDOW_MS) {
            discovery.discover()
                .onEach { macs ->
                    val fingerprint = trustStore.current()?.certFingerprint
                    endpoint.value = fingerprint?.let { fp -> macs.firstOrNull { it.fingerprint == fp }?.let(::endpointOf) }
                }
                .first { endpoint.value != null }
        }
        engine.syncNow()
    }

    /**
     * Fetch a track's lyrics from the paired Mac (sync-protocol.md section 8), for the
     * lyrics repository. Returns [FetchResult.Unreachable] when the Mac is not currently
     * visible or the request fails, and [FetchResult.NotFound] on a 404. Never throws.
     */
    suspend fun fetchLyrics(trackId: Long): FetchResult = withContext(dispatchers.io) {
        val base = endpoint.value ?: return@withContext FetchResult.Unreachable
        val server = trustStore.current() ?: return@withContext FetchResult.Unreachable
        val api = SyncApi(httpClientFactory().pairedClient(server.certFingerprint), dispatchers)
        try {
            val response = api.lyrics(base, trackId)
            val kind = if (response.kind == LyricsKind.Synced.wire) LyricsKind.Synced else LyricsKind.Unsynced
            FetchResult.Found(kind, response.text)
        } catch (expected: SyncError.NotFound) {
            FetchResult.NotFound
        } catch (server: SyncError.Server) {
            if (server.httpStatus == HTTP_NOT_FOUND) FetchResult.NotFound else FetchResult.Unreachable
        } catch (other: SyncError) {
            log.debug("lyrics.fetch.failed", mapOf("error" to other.toString()))
            FetchResult.Unreachable
        }
    }

    /**
     * Fetch an episode's Podcasting 2.0 chapters JSON from the paired Mac (sync-protocol.md
     * section, /v1/chapters), for the chapters repository. Null when the Mac is not visible
     * or the request fails. Never throws.
     */
    suspend fun fetchChapters(episodeId: String): String? = withContext(dispatchers.io) {
        val base = endpoint.value ?: return@withContext null
        val server = trustStore.current() ?: return@withContext null
        val api = SyncApi(httpClientFactory().pairedClient(server.certFingerprint), dispatchers)
        try {
            api.chaptersJson(base, episodeId)
        } catch (failure: SyncError) {
            log.debug("chapters.fetch.failed", mapOf("error" to failure.toString()))
            null
        }
    }

    /** Total bytes under the media root, for the status screen's storage line. */
    suspend fun storageBytes(): Long = withTimeoutOrNull(STORAGE_WALK_MS) {
        mediaLayout.mediaRoot()
            ?.walkBottomUp()
            ?.filter { it.isFile }
            ?.sumOf { it.length() }
            ?: 0L
    } ?: 0L

    fun setAutoSync(enabled: Boolean) {
        settings.setAutoSync(enabled)
        applyWorkerSchedule()
    }

    fun setChargingOnly(enabled: Boolean) {
        settings.setChargingOnly(enabled)
        applyWorkerSchedule()
    }

    private fun applyWorkerSchedule() {
        if (settings.autoSyncEnabled.value) {
            scheduler.schedulePeriodic(settings.chargingOnly.value)
        } else {
            scheduler.cancelPeriodic()
        }
    }

    private suspend fun engine(): SyncEngine? {
        val server = trustStore.current() ?: return null
        val reusable = cached?.takeIf { it.first == server.certFingerprint }?.second
        return reusable ?: buildEngine(server)
    }

    private fun buildEngine(server: SyncServerEntity): SyncEngine {
        val client = httpClientFactory().pairedClient(server.certFingerprint)
        // A 30 s read timeout is the stall detector for file streams.
        val downloadClient = client.newBuilder().readTimeout(STALL_SECONDS, TimeUnit.SECONDS).build()
        val engine = SyncEngine(
            trustStore = { trustStore.current() },
            transport = SyncTransport(
                endpoints = SyncEndpointProvider { endpoint.value },
                api = SyncApi(client, dispatchers),
                transferrer = FileTransferrer(Downloader(downloadClient, dispatchers), mediaLayout, artworkStore, dispatchers)
            ),
            store = SyncStore(
                applier = applier,
                mediaLayout = mediaLayout,
                pendingDownloads = { database.libraryDao().observeDownloadCounts().first().pending }
            ),
            dispatchers = dispatchers,
            scope = appScope
        )
        appScope.launch { engine.state.onEach { syncStateFlow.value = it }.collect {} }
        cached = server.certFingerprint to engine
        log.info("sync.engineReady", mapOf("server" to server.serverName))
        return engine
    }

    private companion object {
        const val STALL_SECONDS = 30L
        const val DISCOVERY_WINDOW_MS = 20_000L
        const val STORAGE_WALK_MS = 5_000L
        const val HTTP_NOT_FOUND = 404
    }
}
