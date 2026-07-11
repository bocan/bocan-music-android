package io.cloudcauldron.bocan.app

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.media3.common.util.UnstableApi
import io.cloudcauldron.bocan.app.data.EqPreferences
import io.cloudcauldron.bocan.app.data.LibraryPreferences
import io.cloudcauldron.bocan.app.data.PodcastPreferences
import io.cloudcauldron.bocan.app.effects.EqualizerViewModel
import io.cloudcauldron.bocan.app.library.AlbumDetailViewModel
import io.cloudcauldron.bocan.app.library.ArtistDetailViewModel
import io.cloudcauldron.bocan.app.library.GenreDetailViewModel
import io.cloudcauldron.bocan.app.library.LibraryViewModel
import io.cloudcauldron.bocan.app.library.PlaylistDetailViewModel
import io.cloudcauldron.bocan.app.pairing.PairingViewModel
import io.cloudcauldron.bocan.app.playback.AndroidMediaFileResolver
import io.cloudcauldron.bocan.app.player.LyricsViewModel
import io.cloudcauldron.bocan.app.player.NowPlayingViewModel
import io.cloudcauldron.bocan.app.player.PlayerViewModel
import io.cloudcauldron.bocan.app.player.QueueViewModel
import io.cloudcauldron.bocan.app.podcasts.PodcastsViewModel
import io.cloudcauldron.bocan.app.podcasts.ShowDetailViewModel
import io.cloudcauldron.bocan.app.search.SearchViewModel
import io.cloudcauldron.bocan.app.settings.PodcastSettingsViewModel
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
import io.cloudcauldron.bocan.playback.PlayerVolume
import io.cloudcauldron.bocan.playback.SleepTimer
import io.cloudcauldron.bocan.playback.audio.EffectsChain
import io.cloudcauldron.bocan.playback.lyrics.LyricsFetcher
import io.cloudcauldron.bocan.playback.lyrics.LyricsRepository
import io.cloudcauldron.bocan.playback.podcast.ChaptersRepository
import io.cloudcauldron.bocan.playback.podcast.EpisodeProgressRecorder
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The single manual dependency-injection wiring point. Later phases extend
 * this class with their object graphs (sync engine, player, ...): every
 * collaborator is constructed here and handed down via constructors.
 */
// The graph is the single wiring point: a wide surface of construction factories is
// its whole job, not a decomposition smell.
@Suppress("TooManyFunctions")
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

    /** The audio effects chain (EQ, bass boost, ReplayGain gain, limiter) shared by the player and settings. */
    val effectsChain = EffectsChain(playbackDispatchers)

    /** Builds the single ExoPlayer with the effects chain inserted in the sink. */
    val playerFactory = PlayerFactory(application, effectsChain)

    /** The on-disk artwork file for a content hash, or null; the UI's Coil resolver. */
    fun artworkFile(hash: String?): File? = hash?.let { artworkStore.existing(it) }

    private val playbackLog: AppLog = AppLog.forCategory(LogCategory.Playback)

    private val mediaItemSource by lazy {
        DatabaseMediaItemSource(
            database.libraryDao(),
            database.podcastDao(),
            MediaItemFactory(mediaFileResolver),
            playbackDispatchers,
            playbackLog
        )
    }

    private val playStatsRecorder by lazy {
        PlayStatsRecorder(database.playStatsDao(), playbackDispatchers, playbackLog)
    }

    private val episodeRecorder by lazy {
        EpisodeProgressRecorder(database.episodeStateDao(), database.podcastDao(), playbackDispatchers, playbackLog)
    }

    private val queuePersistence by lazy {
        QueuePersistence(File(application.filesDir, PLAYBACK_DIR), playbackDispatchers, playbackLog)
    }

    /** The single object graph the PlaybackService builds its session from. */
    val playbackComponents: PlaybackComponents by lazy {
        PlaybackComponents(
            playerFactory,
            mediaItemSource,
            playStatsRecorder,
            episodeRecorder,
            queuePersistence,
            playbackDispatchers
        )
    }

    /** DataStore-backed effects settings, edited by the Equalizer screen and applied to the chain. */
    val eqPreferences: EqPreferences by lazy { EqPreferences(application) }

    /**
     * Start applying persisted effects settings to the chain, on launch and on every
     * change, so the EQ is live even before the Now Playing screen is opened. Called once
     * from the Application; the collection lives for the process.
     */
    fun startEffects() {
        playbackScope.launch {
            eqPreferences.state.collect { effectsChain.applySettings(it) }
        }
    }

    fun equalizerViewModel(): EqualizerViewModel = EqualizerViewModel(eqPreferences, playbackDispatchers)

    private val chaptersFetcher = io.cloudcauldron.bocan.playback.podcast.ChaptersFetcher { episodeId ->
        syncCoordinator.fetchChapters(episodeId)
    }

    val chaptersRepository: ChaptersRepository by lazy {
        ChaptersRepository(chaptersFetcher, playbackDispatchers, playbackLog)
    }

    private val playbackScope = CoroutineScope(SupervisorJob() + playbackDispatchers.main)

    /** The app-facing transport bound to the service session; view models share it in later phases. */
    val queueController: QueueController by lazy {
        QueueController(application, playbackDispatchers, mediaItemSource, playbackScope)
    }

    // Library UI graph (phase 05).

    val libraryPreferences: LibraryPreferences by lazy { LibraryPreferences(application) }

    /** App-session player facade shared by the mini player and every play action. */
    val playerViewModel: PlayerViewModel by lazy { PlayerViewModel(queueController, playbackDispatchers) }

    private val playerVolume: PlayerVolume = object : PlayerVolume {
        override suspend fun getVolume(): Float = queueController.currentVolume()
        override suspend fun setVolume(volume: Float) = queueController.setVolume(volume)
        override suspend fun pause() = queueController.pause()
    }

    /** Emits once per track transition, for the sleep timer's end-of-track mode. */
    private val trackTransitions: Flow<Unit> =
        queueController.state.map { it.current?.mediaId }.distinctUntilChanged().drop(1).map { }

    /** One sleep timer for the app session; Now Playing observes and drives it. */
    val sleepTimer: SleepTimer by lazy { SleepTimer(playerVolume, trackTransitions, playbackDispatchers) }

    private val lyricsFetcher = LyricsFetcher { trackId -> syncCoordinator.fetchLyrics(trackId) }

    private val lyricsRepository by lazy {
        LyricsRepository(database.lyricsDao(), lyricsFetcher, playbackDispatchers, playbackLog)
    }

    fun nowPlayingViewModel(): NowPlayingViewModel = NowPlayingViewModel(
        transport = queueController,
        libraryDao = database.libraryDao(),
        podcastDao = database.podcastDao(),
        chaptersRepository = chaptersRepository,
        preferences = podcastPreferences,
        sleepTimer = sleepTimer,
        dispatchers = playbackDispatchers
    )

    fun queueViewModel(): QueueViewModel = QueueViewModel(queueController, playbackDispatchers)

    fun lyricsViewModel(): LyricsViewModel = LyricsViewModel(queueController, database.libraryDao(), lyricsRepository, playbackDispatchers)

    // Podcasts graph (phase 07).

    val podcastPreferences: PodcastPreferences by lazy { PodcastPreferences(application) }

    fun podcastsViewModel(): PodcastsViewModel = PodcastsViewModel(database.podcastDao(), playbackDispatchers)

    fun podcastSettingsViewModel(): PodcastSettingsViewModel = PodcastSettingsViewModel(podcastPreferences, playbackDispatchers)

    fun showDetailViewModel(podcastId: Long): ShowDetailViewModel = ShowDetailViewModel(
        podcastId = podcastId,
        podcastDao = database.podcastDao(),
        episodeStateDao = database.episodeStateDao(),
        transport = queueController,
        preferences = podcastPreferences,
        dispatchers = playbackDispatchers
    )

    fun libraryViewModel(): LibraryViewModel = LibraryViewModel(
        libraryDao = database.libraryDao(),
        playlistDao = database.playlistDao(),
        syncServer = database.syncDao().observeServer(),
        syncState = syncCoordinator.syncState,
        prefs = libraryPreferences,
        dispatchers = dispatchers
    )

    fun albumDetailViewModel(albumId: Long): AlbumDetailViewModel = AlbumDetailViewModel(albumId, database.libraryDao(), dispatchers)

    fun artistDetailViewModel(artistId: Long): ArtistDetailViewModel = ArtistDetailViewModel(artistId, database.libraryDao(), dispatchers)

    fun playlistDetailViewModel(playlistId: Long): PlaylistDetailViewModel =
        PlaylistDetailViewModel(playlistId, database.playlistDao(), dispatchers)

    fun genreDetailViewModel(genre: String): GenreDetailViewModel = GenreDetailViewModel(genre, database.libraryDao(), dispatchers)

    fun searchViewModel(): SearchViewModel = SearchViewModel(database.searchDao(), libraryPreferences, dispatchers)

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
