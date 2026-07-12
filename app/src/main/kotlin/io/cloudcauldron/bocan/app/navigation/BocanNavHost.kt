package io.cloudcauldron.bocan.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import io.cloudcauldron.bocan.app.AppGraph
import io.cloudcauldron.bocan.app.effects.EqualizerScreen
import io.cloudcauldron.bocan.app.library.AlbumDetailScreen
import io.cloudcauldron.bocan.app.library.ArtistDetailScreen
import io.cloudcauldron.bocan.app.library.GenreDetailScreen
import io.cloudcauldron.bocan.app.library.LibraryCallbacks
import io.cloudcauldron.bocan.app.library.LibraryEmptyActions
import io.cloudcauldron.bocan.app.library.LibraryScreen
import io.cloudcauldron.bocan.app.library.PlaylistDetailScreen
import io.cloudcauldron.bocan.app.onboarding.OnboardingFlow
import io.cloudcauldron.bocan.app.pairing.PairingScreen
import io.cloudcauldron.bocan.app.player.NowPlayingScreen
import io.cloudcauldron.bocan.app.podcasts.PodcastsHomeScreen
import io.cloudcauldron.bocan.app.podcasts.ShowDetailScreen
import io.cloudcauldron.bocan.app.search.SearchScreen
import io.cloudcauldron.bocan.app.settings.ScrobbleSettingsScreen
import io.cloudcauldron.bocan.app.settings.SettingsScreen
import io.cloudcauldron.bocan.app.settings.sections.AboutScreen
import io.cloudcauldron.bocan.app.settings.sections.AppearanceCallbacks
import io.cloudcauldron.bocan.app.settings.sections.AppearanceSettingsScreen
import io.cloudcauldron.bocan.app.settings.sections.PlaybackSettingsScreen
import io.cloudcauldron.bocan.app.settings.sections.PodcastSettingsScreen
import io.cloudcauldron.bocan.app.settings.sections.SyncSettingsScreen
import io.cloudcauldron.bocan.sync.pairing.PairingState

/**
 * The single navigation graph: the four bottom destinations plus the detail, search,
 * pairing, and sync routes. Each destination creates its view model from [AppGraph] and
 * disposes it when it leaves the back stack. Detail routes expose a bocan:// deep link.
 */
@Suppress("LongMethod")
@Composable
fun BocanNavHost(navController: NavHostController, appGraph: AppGraph, callbacks: LibraryCallbacks, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Destination.Library, modifier = modifier) {
        composable<Destination.Library> {
            val vm = remember { appGraph.libraryViewModel() }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            LibraryScreen(
                viewModel = vm,
                callbacks = callbacks,
                emptyActions = LibraryEmptyActions(
                    onPair = { navController.navigate(Destination.Pairing) },
                    onSyncNow = { navController.navigate(Destination.SyncStatus) }
                )
            )
        }
        composable<Destination.Podcasts> {
            val vm = remember { appGraph.podcastsViewModel() }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            PodcastsHomeScreen(
                viewModel = vm,
                onOpenShow = { navController.navigate(Destination.ShowDetail(it)) },
                onResume = { appGraph.playerViewModel.playEpisodes(listOf(it), 0) },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable<Destination.ShowDetail> { entry ->
            val vm = remember { appGraph.showDetailViewModel(entry.toRoute<Destination.ShowDetail>().podcastId) }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            ShowDetailScreen(vm, onBack = { navController.popBackStack() })
        }
        composable<Destination.Search> {
            val vm = remember { appGraph.searchViewModel() }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            SearchScreen(viewModel = vm, callbacks = callbacks)
        }
        composable<Destination.Settings> {
            SettingsScreen(
                onOpenSync = { navController.navigate(Destination.SyncStatus) },
                onOpenPlayback = { navController.navigate(Destination.PlaybackSettings) },
                onOpenPodcasts = { navController.navigate(Destination.PodcastSettings) },
                onOpenScrobbling = { navController.navigate(Destination.ScrobbleSettings) },
                onOpenAppearance = { navController.navigate(Destination.AppearanceSettings) },
                onOpenAbout = { navController.navigate(Destination.About) }
            )
        }
        composable<Destination.PlaybackSettings> {
            PlaybackSettingsScreen(
                resumeOnReconnect = appGraph.playbackPreferences.resumeOnReconnect,
                onSetResumeOnReconnect = appGraph::setResumeOnReconnect,
                onOpenEqualizer = { navController.navigate(Destination.Equalizer) },
                onBack = { navController.popBackStack() }
            )
        }
        composable<Destination.PodcastSettings> {
            val vm = remember { appGraph.podcastSettingsViewModel() }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            PodcastSettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable<Destination.About> {
            AboutScreen(
                onShowTour = { navController.navigate(Destination.Onboarding) },
                onBack = { navController.popBackStack() }
            )
        }
        composable<Destination.Onboarding> {
            OnboardingFlow(
                pairingViewModelFactory = appGraph::pairingViewModel,
                syncState = appGraph.syncCoordinator.syncState,
                onStartSync = appGraph::syncNow,
                onCancelSync = appGraph.syncCoordinator::cancelSync,
                onFinished = {
                    appGraph.completeOnboarding()
                    navController.popBackStack()
                }
            )
        }
        composable<Destination.AlbumDetail>(
            deepLinks = listOf(navDeepLink<Destination.AlbumDetail>(basePath = Destination.DEEP_LINK_ALBUM))
        ) { entry ->
            val vm = remember { appGraph.albumDetailViewModel(entry.toRoute<Destination.AlbumDetail>().albumId) }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            AlbumDetailScreen(vm.state, callbacks, navController::popBackStack)
        }
        composable<Destination.ArtistDetail>(
            deepLinks = listOf(navDeepLink<Destination.ArtistDetail>(basePath = Destination.DEEP_LINK_ARTIST))
        ) { entry ->
            val vm = remember { appGraph.artistDetailViewModel(entry.toRoute<Destination.ArtistDetail>().artistId) }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            ArtistDetailScreen(vm.state, callbacks, navController::popBackStack)
        }
        composable<Destination.PlaylistDetail>(
            deepLinks = listOf(navDeepLink<Destination.PlaylistDetail>(basePath = Destination.DEEP_LINK_PLAYLIST))
        ) { entry ->
            val vm = remember { appGraph.playlistDetailViewModel(entry.toRoute<Destination.PlaylistDetail>().playlistId) }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            PlaylistDetailScreen(vm.state, callbacks, navController::popBackStack)
        }
        composable<Destination.GenreDetail> { entry ->
            val vm = remember { appGraph.genreDetailViewModel(entry.toRoute<Destination.GenreDetail>().genre) }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            GenreDetailScreen(vm.state, callbacks, navController::popBackStack)
        }
        composable<Destination.NowPlaying>(
            deepLinks = listOf(navDeepLink { uriPattern = "bocan://nowplaying" })
        ) {
            val nowPlaying = remember { appGraph.nowPlayingViewModel() }
            val queue = remember { appGraph.queueViewModel() }
            val lyrics = remember { appGraph.lyricsViewModel() }
            val songDetails = remember { appGraph.songDetailsViewModel() }
            DisposableEffect(Unit) {
                onDispose {
                    nowPlaying.dispose()
                    queue.dispose()
                    lyrics.dispose()
                    songDetails.dispose()
                }
            }
            NowPlayingScreen(
                nowPlaying = nowPlaying,
                queue = queue,
                lyrics = lyrics,
                songDetails = songDetails,
                onBack = { navController.popBackStack() },
                onOpenArtist = { navController.navigate(Destination.ArtistDetail(it)) },
                onOpenAlbum = { navController.navigate(Destination.AlbumDetail(it)) },
                onOpenEqualizer = { navController.navigate(Destination.Equalizer) }
            )
        }
        composable<Destination.Equalizer> {
            val vm = remember { appGraph.equalizerViewModel() }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            EqualizerScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable<Destination.ScrobbleSettings> {
            val vm = remember { appGraph.scrobbleSettingsViewModel() }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            ScrobbleSettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable<Destination.AppearanceSettings> {
            AppearanceSettingsScreen(
                settings = appGraph.appearancePreferences.settings,
                callbacks = AppearanceCallbacks(
                    onSetThemeMode = appGraph::setThemeMode,
                    onSetDynamicColor = appGraph::setDynamicColor,
                    onSetPureBlack = appGraph::setPureBlack,
                    onBack = { navController.popBackStack() }
                )
            )
        }
        composable<Destination.Pairing> {
            val vm = remember { appGraph.pairingViewModel() }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            val uiState by vm.state.collectAsState()
            LaunchedEffect(uiState.pairing) {
                if (uiState.pairing is PairingState.Paired) {
                    // Pairing done: start the first sync and land on the library, which shows
                    // sync progress. Otherwise the user is stranded on the pairing screen.
                    appGraph.syncNow()
                    navController.navigate(Destination.Library) {
                        popUpTo(Destination.Library) { inclusive = true }
                    }
                }
            }
            PairingScreen(vm)
        }
        composable<Destination.SyncStatus> {
            val vm = remember { appGraph.syncStatusViewModel() }
            DisposableEffect(Unit) { onDispose { vm.dispose() } }
            SyncSettingsScreen(
                viewModel = vm,
                onPair = { navController.navigate(Destination.Pairing) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
