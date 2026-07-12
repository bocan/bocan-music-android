package io.cloudcauldron.bocan.app

import android.app.Application
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.provider.Settings
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import io.cloudcauldron.bocan.app.data.AppearancePreferences
import io.cloudcauldron.bocan.app.data.EqPreferences
import io.cloudcauldron.bocan.app.data.LibraryPreferences
import io.cloudcauldron.bocan.app.data.PlaybackPreferences
import io.cloudcauldron.bocan.app.data.PodcastPreferences
import io.cloudcauldron.bocan.app.data.ScrobbleSettings
import io.cloudcauldron.bocan.app.data.ThemeMode
import io.cloudcauldron.bocan.app.effects.EqualizerViewModel
import io.cloudcauldron.bocan.app.library.AlbumDetailViewModel
import io.cloudcauldron.bocan.app.library.ArtistDetailViewModel
import io.cloudcauldron.bocan.app.library.GenreDetailViewModel
import io.cloudcauldron.bocan.app.library.LibraryViewModel
import io.cloudcauldron.bocan.app.library.PlaylistDetailViewModel
import io.cloudcauldron.bocan.app.pairing.PairingViewModel
import io.cloudcauldron.bocan.app.playback.AndroidMediaFileResolver
import io.cloudcauldron.bocan.app.playback.HeadphoneReconnectMonitor
import io.cloudcauldron.bocan.app.player.LyricsViewModel
import io.cloudcauldron.bocan.app.player.NowPlayingViewModel
import io.cloudcauldron.bocan.app.player.PlayerViewModel
import io.cloudcauldron.bocan.app.player.QueueViewModel
import io.cloudcauldron.bocan.app.podcasts.PodcastsViewModel
import io.cloudcauldron.bocan.app.podcasts.ShowDetailViewModel
import io.cloudcauldron.bocan.app.search.SearchViewModel
import io.cloudcauldron.bocan.app.settings.PodcastSettingsViewModel
import io.cloudcauldron.bocan.app.settings.ScrobbleSettingsViewModel
import io.cloudcauldron.bocan.app.sync.SyncCoordinator
import io.cloudcauldron.bocan.app.sync.SyncSettings
import io.cloudcauldron.bocan.app.sync.SyncStatusViewModel
import io.cloudcauldron.bocan.app.widget.WidgetStateStore
import io.cloudcauldron.bocan.app.widget.WidgetUpdater
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.persistence.SyncApplier
import io.cloudcauldron.bocan.playback.CoroutineDispatchers as PlaybackDispatchers
import io.cloudcauldron.bocan.playback.DatabaseMediaItemSource
import io.cloudcauldron.bocan.playback.MediaId
import io.cloudcauldron.bocan.playback.MediaItemFactory
import io.cloudcauldron.bocan.playback.PlaybackComponents
import io.cloudcauldron.bocan.playback.PlayerFactory
import io.cloudcauldron.bocan.playback.PlayerVolume
import io.cloudcauldron.bocan.playback.SleepTimer
import io.cloudcauldron.bocan.playback.audio.EffectsChain
import io.cloudcauldron.bocan.playback.browse.BrowseLabels
import io.cloudcauldron.bocan.playback.browse.MediaTree
import io.cloudcauldron.bocan.playback.lyrics.LyricsFetcher
import io.cloudcauldron.bocan.playback.lyrics.LyricsRepository
import io.cloudcauldron.bocan.playback.podcast.ChaptersRepository
import io.cloudcauldron.bocan.playback.podcast.EpisodeProgressRecorder
import io.cloudcauldron.bocan.playback.queue.QueueController
import io.cloudcauldron.bocan.playback.queue.QueuePersistence
import io.cloudcauldron.bocan.playback.session.SessionCommands
import io.cloudcauldron.bocan.playback.stats.PlayStatsRecorder
import io.cloudcauldron.bocan.scrobble.CoroutineDispatchers as ScrobbleDispatchers
import io.cloudcauldron.bocan.scrobble.ScrobbleService
import io.cloudcauldron.bocan.scrobble.ScrobbleTrack
import io.cloudcauldron.bocan.scrobble.auth.KeystoreTokenStore
import io.cloudcauldron.bocan.scrobble.net.ScrobbleHttp
import io.cloudcauldron.bocan.scrobble.providers.LastFmConfig
import io.cloudcauldron.bocan.scrobble.providers.LastFmProvider
import io.cloudcauldron.bocan.scrobble.providers.ListenBrainzProvider
import io.cloudcauldron.bocan.scrobble.providers.RockskyProvider
import io.cloudcauldron.bocan.scrobble.providers.ScrobbleProvider
import io.cloudcauldron.bocan.scrobble.queue.ScrobbleQueue
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
import okhttp3.OkHttpClient

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

    /** The Android Auto browse tree; category titles stay localized in the app's resources. */
    private val mediaTree by lazy {
        MediaTree(
            browseDao = database.browseDao(),
            labels = BrowseLabels(
                continueListening = application.getString(R.string.browse_continue),
                playlists = application.getString(R.string.browse_playlists),
                albums = application.getString(R.string.browse_albums),
                artists = application.getString(R.string.browse_artists),
                podcasts = application.getString(R.string.browse_podcasts),
                songs = application.getString(R.string.browse_songs)
            ),
            artworkUri = { hash -> hash?.let(mediaFileResolver::artworkUri) },
            dispatchers = playbackDispatchers
        )
    }

    /** The notification and Auto skip buttons shown for an episode, localized here. */
    private val episodeSkipButtons: List<CommandButton> by lazy {
        listOf(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
                .setDisplayName(application.getString(R.string.action_skip_back))
                .setSessionCommand(SessionCommands.command(SessionCommands.SKIP_BACK))
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                .setDisplayName(application.getString(R.string.action_skip_forward))
                .setSessionCommand(SessionCommands.command(SessionCommands.SKIP_FORWARD))
                .build()
        )
    }

    /** The single object graph the PlaybackService builds its session from. */
    val playbackComponents: PlaybackComponents by lazy {
        PlaybackComponents(
            playerFactory,
            mediaItemSource,
            playStatsRecorder,
            episodeRecorder,
            queuePersistence,
            mediaTree,
            episodeSkipButtons,
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

    // Scrobble graph (phase 09). Providers behind one interface, an offline queue, and the
    // service that bridges the phase 04 stats flow. Credentials live in the Keystore-backed
    // token store, never the database.

    private val scrobbleDispatchers = ScrobbleDispatchers()

    val scrobbleSettings: ScrobbleSettings by lazy { ScrobbleSettings(application) }

    private val tokenStore by lazy { KeystoreTokenStore(application, scrobbleDispatchers) }

    private val scrobbleHttp by lazy { ScrobbleHttp(OkHttpClient(), scrobbleDispatchers) }

    private val lastFmConfig = LastFmConfig(BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_SHARED_SECRET)

    /** Null when the build carries no Last.fm key, which hides the provider rather than crashing. */
    private val lastFmProvider: LastFmProvider? by lazy {
        if (lastFmConfig.isConfigured) LastFmProvider(lastFmConfig, tokenStore, scrobbleHttp) else null
    }

    private val scrobbleProviders: List<ScrobbleProvider> by lazy {
        listOfNotNull(lastFmProvider, ListenBrainzProvider(tokenStore, scrobbleHttp), RockskyProvider(tokenStore, scrobbleHttp))
    }

    private val scrobbleQueue by lazy { ScrobbleQueue(database.scrobbleDao(), scrobbleDispatchers) }

    private val scrobbleService: ScrobbleService by lazy {
        ScrobbleService(
            providers = scrobbleProviders,
            queue = scrobbleQueue,
            metadata = ::resolveScrobbleTrack,
            enabledProviders = ::enabledScrobbleProviders,
            scope = playbackScope,
            dispatchers = scrobbleDispatchers
        )
    }

    /**
     * Start pushing playback state to the home-screen widget for the life of the process.
     * It observes the shared transport, which the app's player surfaces connect to the
     * session; the widget need not open its own controller.
     */
    fun startWidget() {
        WidgetUpdater(application, queueController.state, WidgetStateStore(application), playbackScope).start()
        headphoneReconnectMonitor.start()
    }

    /** Playback-behaviour settings (resume on reconnect). */
    val playbackPreferences: PlaybackPreferences by lazy { PlaybackPreferences(application) }

    /** Theme mode, dynamic color, and pure-black dark; the activity's theme observes these. */
    val appearancePreferences: AppearancePreferences by lazy { AppearancePreferences(application) }

    fun setThemeMode(mode: ThemeMode) {
        playbackScope.launch { appearancePreferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        playbackScope.launch { appearancePreferences.setDynamicColor(enabled) }
    }

    fun setPureBlack(enabled: Boolean) {
        playbackScope.launch { appearancePreferences.setPureBlack(enabled) }
    }

    private val headphoneReconnectMonitor: HeadphoneReconnectMonitor by lazy {
        HeadphoneReconnectMonitor(
            audioManager = application.getSystemService(AudioManager::class.java),
            resumeEnabled = { playbackPreferences.resumeOnReconnectNow() },
            onReconnect = ::resumeIfPaused,
            scope = playbackScope,
            dispatchers = playbackDispatchers
        )
    }

    fun setResumeOnReconnect(enabled: Boolean) {
        playbackScope.launch { playbackPreferences.setResumeOnReconnect(enabled) }
    }

    /** Resume only when playback is paused with content, through the connected session controller. */
    private fun resumeIfPaused() {
        val state = queueController.state.value
        if (!state.isPlaying && state.current != null) playerViewModel.togglePlayPause()
    }

    /**
     * Route a `bocan://` launcher shortcut to its action. The `nowplaying` deep link is
     * handled by the navigation graph, not here; the rest are one-shot playback or sync
     * actions dispatched through the shared player facade.
     */
    fun handleShortcut(host: String?) {
        when (host) {
            SHORTCUT_RESUME -> playerViewModel.togglePlayPause()
            SHORTCUT_SHUFFLE -> playbackScope.launch {
                val ids = database.libraryDao().downloadedTrackIds()
                if (ids.isNotEmpty()) playerViewModel.shuffle(ids)
            }
            SHORTCUT_CONTINUE -> playbackScope.launch {
                database.browseDao().continueListeningPage(limit = 1, offset = 0).firstOrNull()?.let {
                    playerViewModel.playEpisodes(listOf(it.id), 0)
                }
            }
            SHORTCUT_SYNC -> SyncForegroundService.start(application, force = true)
            else -> Unit
        }
    }

    fun scrobbleSettingsViewModel(): ScrobbleSettingsViewModel = ScrobbleSettingsViewModel(
        providers = scrobbleProviders,
        settings = scrobbleSettings,
        queue = scrobbleQueue,
        tokens = tokenStore,
        lastFm = lastFmProvider,
        dispatchers = scrobbleDispatchers
    )

    /**
     * Bridge the phase 04 stats flow into the scrobbler (:core:scrobble cannot import
     * :core:playback), send now-playing when a track starts, and drain the queue on launch.
     * Called once from the Application.
     */
    fun startScrobbling() {
        playbackScope.launch {
            playStatsRecorder.scrobbleEvents.collect { event ->
                val trackId = event.trackId ?: return@collect
                scrobbleService.onPlayEligible(trackId, event.playedAt, event.isPodcast)
            }
        }
        playbackScope.launch {
            var lastSignalled: String? = null
            queueController.state.collect { state ->
                val current = state.current?.mediaId
                if (state.isPlaying && current != null && current != lastSignalled) {
                    lastSignalled = current
                    (MediaId.parse(current) as? MediaId.Track)?.let { scrobbleService.onNowPlaying(it.trackId, isPodcast = false) }
                }
            }
        }
        registerConnectivityDrain()
        scrobbleService.drain()
    }

    /** Drain the queue whenever a network becomes available (ACCESS_NETWORK_STATE is merged from :core:sync). */
    private fun registerConnectivityDrain() {
        val manager = application.getSystemService(ConnectivityManager::class.java) ?: return
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scrobbleService.drain()
                }
            }
        )
    }

    private suspend fun resolveScrobbleTrack(trackId: Long): ScrobbleTrack? =
        database.libraryDao().tracksByIds(listOf(trackId)).firstOrNull()?.let { track ->
            ScrobbleTrack(
                title = track.title,
                artist = track.artistName,
                album = track.albumName,
                albumArtist = track.albumArtistName,
                durationSec = (track.durationMs / MS_PER_SECOND).toInt()
            )
        }

    private suspend fun enabledScrobbleProviders(): Set<String> {
        val toggles = scrobbleSettings.current()
        if (!toggles.masterEnabled) return emptySet()
        return toggles.enabledProviders intersect scrobbleProviders.map { it.id }.toSet()
    }

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

    /** A fresh view model for the sync settings surface; the caller disposes it. */
    fun syncStatusViewModel(): SyncStatusViewModel = SyncStatusViewModel(
        sources = SyncStatusViewModel.Sources(
            syncState = syncCoordinator.syncState,
            server = database.syncDao().observeServer(),
            counts = database.libraryDao().observeDownloadCounts(),
            toggles = SyncStatusViewModel.ToggleFlows(
                syncOnDiscovery = syncSettings.syncOnDiscovery,
                periodicSync = syncSettings.periodicSync,
                chargingOnly = syncSettings.chargingOnly
            ),
            storageBytes = syncCoordinator::storageBytes
        ),
        actions = SyncStatusViewModel.Actions(
            syncNow = { SyncForegroundService.start(application, force = true) },
            cancel = syncCoordinator::cancelSync,
            toggles = SyncStatusViewModel.ToggleActions(
                setSyncOnDiscovery = syncCoordinator::setSyncOnDiscovery,
                setPeriodicSync = syncCoordinator::setPeriodicSync,
                setChargingOnly = syncCoordinator::setChargingOnly
            ),
            unpair = syncCoordinator::unpair,
            removeAllMedia = ::removeAllSyncedMedia
        ),
        dispatchers = dispatchers
    )

    /** Start a foreground sync now: the post-pairing handoff and the Sync Now action share this. */
    fun syncNow() = SyncForegroundService.start(application, force = true)

    /**
     * The confirmed remove-all-synced-media action: stop and clear playback first
     * (the files are about to vanish under the player), then delete and flip the
     * library back to pending. Pairing and play history are untouched.
     */
    suspend fun removeAllSyncedMedia() {
        queueController.clear()
        syncCoordinator.removeAllSyncedMedia()
    }

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
        const val MS_PER_SECOND = 1000L
        const val SHORTCUT_RESUME = "resume"
        const val SHORTCUT_SHUFFLE = "shuffle"
        const val SHORTCUT_CONTINUE = "continue"
        const val SHORTCUT_SYNC = "sync"
    }
}
