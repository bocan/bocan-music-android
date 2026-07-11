package io.cloudcauldron.bocan.app

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.media3.common.util.UnstableApi
import io.cloudcauldron.bocan.app.pairing.PairingViewModel
import io.cloudcauldron.bocan.app.playback.AndroidMediaFileResolver
import io.cloudcauldron.bocan.app.sync.SyncCoordinator
import io.cloudcauldron.bocan.app.sync.SyncSettings
import io.cloudcauldron.bocan.app.sync.SyncStatusViewModel
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.persistence.SyncApplier
import io.cloudcauldron.bocan.playback.CoroutineDispatchers as PlaybackDispatchers
import io.cloudcauldron.bocan.playback.DatabaseMediaItemSource
import io.cloudcauldron.bocan.playback.MediaItemFactory
import io.cloudcauldron.bocan.playback.PlaybackComponents
import io.cloudcauldron.bocan.playback.PlayerFactory
import io.cloudcauldron.bocan.playback.queue.QueueController
import io.cloudcauldron.bocan.playback.queue.QueuePersistence
import io.cloudcauldron.bocan.playback.stats.PlayStatsRecorder
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.discovery.MacDiscovery
import io.cloudcauldron.bocan.sync.engine.ArtworkStore
import io.cloudcauldron.bocan.sync.engine.MediaLayout
import io.cloudcauldron.bocan.sync.identity.DeviceIdentity
import io.cloudcauldron.bocan.sync.identity.KeystoreDeviceIdentity
import io.cloudcauldron.bocan.sync.net.SyncHttpClientFactory
import io.cloudcauldron.bocan.sync.net.TrustStore
import io.cloudcauldron.bocan.sync.pairing.PairingClient
import io.cloudcauldron.bocan.sync.service.SyncForegroundService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The single manual dependency-injection wiring point. Later phases extend
 * this class with their object graphs (sync engine, player, ...): every
 * collaborator is constructed here and handed down via constructors.
 */
@OptIn(UnstableApi::class)
class AppGraph(val application: Application) {
    val appLog: AppLog = AppLog.forCategory(LogCategory.App)

    val dispatchers = CoroutineDispatchers()

    /** Lazy so a cold launch does not open the database before first use. */
    val database: BocanDatabase by lazy {
        BocanDatabase.create(application, Dispatchers.IO)
    }

    val syncApplier: SyncApplier by lazy { SyncApplier(database) }

    /** Lazy: the Keystore key and certificate are provisioned only when pairing or syncing begins. */
    val deviceIdentity: DeviceIdentity by lazy { KeystoreDeviceIdentity.loadOrCreate() }

    val httpClientFactory: SyncHttpClientFactory by lazy { SyncHttpClientFactory(deviceIdentity) }

    val trustStore: TrustStore by lazy { TrustStore(database.syncDao()) }

    val macDiscovery: MacDiscovery by lazy { MacDiscovery.create(application, dispatchers) }

    val syncSettings: SyncSettings by lazy { SyncSettings(application) }

    /** The single sync graph and the SyncHost the service and worker reach through the Application. */
    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(
            context = application,
            dispatchers = dispatchers,
            database = database,
            httpClientFactory = { httpClientFactory },
            discovery = macDiscovery,
            settings = syncSettings
        )
    }

    // Playback graph (phase 04). The service reaches these via PlaybackHost.

    private val playbackDispatchers = PlaybackDispatchers()

    private val mediaLayout = MediaLayout(application)

    private val artworkStore = ArtworkStore(mediaLayout)

    private val mediaFileResolver = AndroidMediaFileResolver(mediaLayout, artworkStore)

    /** Holds the ReplayGain processor seam and audio session id for phase 08. */
    val playerFactory = PlayerFactory(application)

    private val playbackLog: AppLog = AppLog.forCategory(LogCategory.Playback)

    private val mediaItemSource by lazy {
        DatabaseMediaItemSource(database.libraryDao(), MediaItemFactory(mediaFileResolver), playbackDispatchers, playbackLog)
    }

    private val playStatsRecorder by lazy {
        PlayStatsRecorder(database.playStatsDao(), playbackDispatchers, playbackLog)
    }

    private val queuePersistence by lazy {
        QueuePersistence(File(application.filesDir, PLAYBACK_DIR), playbackDispatchers, playbackLog)
    }

    /** The single object graph the PlaybackService builds its session from. */
    val playbackComponents: PlaybackComponents by lazy {
        PlaybackComponents(playerFactory, mediaItemSource, playStatsRecorder, queuePersistence, playbackDispatchers)
    }

    private val playbackScope = CoroutineScope(SupervisorJob() + playbackDispatchers.main)

    /** The app-facing transport bound to the service session; view models share it in later phases. */
    val queueController: QueueController by lazy {
        QueueController(application, playbackDispatchers, mediaItemSource, playbackScope)
    }

    /**
     * Temporary debug affordance (phase 04): connect to the session and play the
     * first album end to end, proving audio works before phase 05 builds the library
     * screens. Replaced by the real library UI.
     */
    fun playFirstAlbum() {
        playbackScope.launch {
            val albums = database.libraryDao().observeAlbumsByName().first()
            val album = albums.firstOrNull() ?: return@launch
            val trackIds = database.libraryDao().observeTracksForAlbum(album.id).first().map { it.id }
            if (trackIds.isEmpty()) return@launch
            queueController.connect()
            queueController.playNow(trackIds, startIndex = 0)
        }
    }

    /** A fresh view model for the sync status screen; the caller disposes it. */
    fun syncStatusViewModel(): SyncStatusViewModel = SyncStatusViewModel(
        sources = SyncStatusViewModel.Sources(
            syncState = syncCoordinator.syncState,
            server = database.syncDao().observeServer(),
            counts = database.libraryDao().observeDownloadCounts(),
            autoSync = syncSettings.autoSyncEnabled,
            chargingOnly = syncSettings.chargingOnly,
            storageBytes = syncCoordinator::storageBytes
        ),
        actions = SyncStatusViewModel.Actions(
            syncNow = { SyncForegroundService.start(application, force = true) },
            cancel = syncCoordinator::cancelSync,
            setAutoSync = syncCoordinator::setAutoSync,
            setChargingOnly = syncCoordinator::setChargingOnly
        ),
        dispatchers = dispatchers
    )

    /** A fresh view model for one pairing flow; the caller disposes it. */
    fun pairingViewModel(): PairingViewModel = PairingViewModel(
        discovery = macDiscovery,
        pairingClient = PairingClient(
            identity = deviceIdentity,
            clientFactory = httpClientFactory,
            trustStore = trustStore,
            deviceName = deviceName(),
            dispatchers = dispatchers
        ),
        dispatchers = dispatchers
    )

    private fun deviceName(): String = Settings.Global.getString(application.contentResolver, Settings.Global.DEVICE_NAME)
        ?: Build.MODEL
        ?: DEFAULT_DEVICE_NAME

    private companion object {
        const val DEFAULT_DEVICE_NAME = "Android phone"
        const val PLAYBACK_DIR = "playback"
    }
}
