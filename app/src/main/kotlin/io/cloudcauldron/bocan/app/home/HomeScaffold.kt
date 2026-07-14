package io.cloudcauldron.bocan.app.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.cloudcauldron.bocan.app.AppGraph
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.library.LibraryCallbacks
import io.cloudcauldron.bocan.app.navigation.BocanNavHost
import io.cloudcauldron.bocan.app.navigation.Destination
import io.cloudcauldron.bocan.app.navigation.bottomDestinations
import io.cloudcauldron.bocan.app.player.MiniPlayerBar
import kotlinx.coroutines.launch

/**
 * The app shell: the bottom navigation bar, the mini player docked above it (shown when
 * a session item exists), and the navigation graph in the body. Owns the one NavController
 * and builds the [LibraryCallbacks] every library surface uses, wiring playback to the
 * shared player and navigation to detail routes.
 */
@Composable
fun HomeScaffold(appGraph: AppGraph, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val player = appGraph.playerViewModel
    val playerState by player.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pendingMessage = stringResource(R.string.track_pending_toast)

    val callbacks = remember(navController) {
        LibraryCallbacks(
            openAlbum = { navController.navigate(Destination.AlbumDetail(it)) },
            openArtist = { navController.navigate(Destination.ArtistDetail(it)) },
            openPlaylist = { navController.navigate(Destination.PlaylistDetail(it)) },
            openGenre = { navController.navigate(Destination.GenreDetail(it)) },
            playContext = { ids, index -> player.play(ids, index) },
            shuffle = { player.shuffle(it) },
            shuffleAll = appGraph::shuffleAllDownloaded,
            playNext = { player.playNext(it) },
            addToQueue = { player.addToQueue(it) },
            explainPending = { scope.launch { snackbarHostState.showSnackbar(pendingMessage) } }
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    // Now Playing is a full-screen overlay: the mini player and the bottom tab bar are both
    // hidden while it is up, so it reads as an expanded mini player (dismissed by the chevron
    // or a swipe down) rather than a tab-bar screen. The tab bar reappears on collapse.
    val onNowPlaying = backStackEntry?.destination?.hierarchy?.any { it.hasRoute(Destination.NowPlaying::class) } == true
    // Landscape has little height, so the tabs move to a navigation rail on the left rather than
    // eating a bottom row. Both the mini player and the navigation hide while Now Playing is up.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val miniPlayer: @Composable () -> Unit = {
        if (!onNowPlaying) {
            MiniPlayerBar(
                state = playerState,
                onPlayPause = player::togglePlayPause,
                onTap = { navController.navigate(Destination.NowPlaying) }
            )
        }
    }

    // Each screen owns its own top inset (a TopAppBar, or its own status-bar padding), so the
    // shell must not also reserve the status bar, or the two stack into a doubled gap.
    if (landscape) {
        Row(modifier = modifier.fillMaxSize()) {
            if (!onNowPlaying) HomeNavRail(navController)
            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = { miniPlayer() }
            ) { padding -> BocanNavHost(navController, appGraph, callbacks, Modifier.padding(padding)) }
        }
    } else {
        Scaffold(
            modifier = modifier,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!onNowPlaying) {
                    Column {
                        miniPlayer()
                        HomeBottomBar(navController)
                    }
                }
            }
        ) { padding -> BocanNavHost(navController, appGraph, callbacks, Modifier.padding(padding)) }
    }
}

@Composable
private fun HomeNavRail(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    NavigationRail {
        bottomDestinations.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination::class) } == true
            NavigationRailItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(bottomIcon(destination), contentDescription = null) },
                label = { Text(stringResource(bottomLabel(destination))) }
            )
        }
    }
}

@Composable
private fun HomeBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    NavigationBar {
        bottomDestinations.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination::class) } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(bottomIcon(destination), contentDescription = null) },
                label = { Text(stringResource(bottomLabel(destination))) }
            )
        }
    }
}

private fun bottomIcon(destination: Destination): ImageVector = when (destination) {
    Destination.Podcasts -> Icons.Rounded.Podcasts
    Destination.Search -> Icons.Rounded.Search
    Destination.Settings -> Icons.Rounded.Settings
    else -> Icons.Rounded.LibraryMusic
}

private fun bottomLabel(destination: Destination): Int = when (destination) {
    Destination.Podcasts -> R.string.tab_podcasts
    Destination.Search -> R.string.tab_search
    Destination.Settings -> R.string.tab_settings
    else -> R.string.tab_library
}
