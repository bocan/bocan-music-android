package io.cloudcauldron.bocan.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.cloudcauldron.bocan.app.components.LocalArtworkResolver
import io.cloudcauldron.bocan.app.data.AppearanceSettings
import io.cloudcauldron.bocan.app.home.HomeScaffold
import io.cloudcauldron.bocan.app.onboarding.AppEntry
import io.cloudcauldron.bocan.app.onboarding.OnboardingFlow
import io.cloudcauldron.bocan.app.theme.BocanTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appGraph = (application as BocanApplication).appGraph
        handleShortcutIntent(intent)
        setContent {
            val appearance by appGraph.appearancePreferences.settings.collectAsState(initial = AppearanceSettings())
            BocanTheme(appearance) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RequestNotificationPermission()
                    CompositionLocalProvider(LocalArtworkResolver provides { hash: String? -> appGraph.artworkFile(hash) }) {
                        AppRoot(appGraph)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    /**
     * Dispatch a `bocan://` launcher shortcut (resume, shuffle, continue, sync) to its
     * action. The `nowplaying` deep link is left to the navigation graph, which resolves it.
     */
    private fun handleShortcutIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == DEEP_LINK_SCHEME && data.host != NOW_PLAYING_HOST) {
            (application as BocanApplication).appGraph.handleShortcut(data.host)
        }
    }

    private companion object {
        const val DEEP_LINK_SCHEME = "bocan"
        const val NOW_PLAYING_HOST = "nowplaying"
    }
}

/**
 * The root gate: a fresh unpaired install gets the welcome flow, everyone else
 * the library. The first resolved decision is latched, so pairing mid-flow
 * cannot unmount onboarding before its first-sync step; only finishing (or
 * skipping) the flow moves past it.
 */
@Composable
private fun AppRoot(appGraph: AppGraph) {
    var entry by remember { mutableStateOf(AppEntry.Undetermined) }
    LaunchedEffect(Unit) {
        if (entry == AppEntry.Undetermined) {
            entry = appGraph.appEntry().first { it != AppEntry.Undetermined }
        }
    }
    when (entry) {
        AppEntry.Undetermined -> Unit
        AppEntry.Onboarding -> OnboardingFlow(
            pairingViewModelFactory = appGraph::pairingViewModel,
            syncState = appGraph.syncCoordinator.syncState,
            onStartSync = appGraph::syncNow,
            onCancelSync = appGraph.syncCoordinator::cancelSync,
            onFinished = {
                appGraph.completeOnboarding()
                entry = AppEntry.Home
            }
        )
        AppEntry.Home -> HomeScaffold(appGraph)
    }
}

/** Ask for the notification permission (Android 13+) so sync and playback notifications can show. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
