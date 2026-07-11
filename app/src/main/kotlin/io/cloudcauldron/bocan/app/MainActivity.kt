package io.cloudcauldron.bocan.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.cloudcauldron.bocan.app.pairing.PairingScreen
import io.cloudcauldron.bocan.app.sync.SyncStatusScreen
import io.cloudcauldron.bocan.app.theme.BocanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appGraph = (application as BocanApplication).appGraph
        setContent {
            BocanTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BocanApp(appGraph)
                }
            }
        }
    }
}

private enum class Route { Home, Pairing, SyncStatus }

@Composable
private fun BocanApp(appGraph: AppGraph) {
    var route by rememberSaveable { mutableStateOf(Route.Home) }
    when (route) {
        Route.Pairing -> {
            val viewModel = remember { appGraph.pairingViewModel() }
            DisposableEffect(Unit) { onDispose { viewModel.dispose() } }
            PairingScreen(viewModel)
        }
        Route.SyncStatus -> {
            RequestNotificationPermission()
            val viewModel = remember { appGraph.syncStatusViewModel() }
            DisposableEffect(Unit) { onDispose { viewModel.dispose() } }
            SyncStatusScreen(viewModel)
        }
        Route.Home -> HomeScreen(
            onOpenPairing = { route = Route.Pairing },
            onOpenSync = { route = Route.SyncStatus }
        )
    }
}

/** Ask for the notification permission (Android 13+) so the sync progress notification can show. */
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

@Composable
private fun HomeScreen(onOpenPairing: () -> Unit, onOpenSync: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = stringResource(R.string.wordmark),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Button(onClick = onOpenPairing) {
                Text(stringResource(R.string.home_pair_action))
            }
            Button(onClick = onOpenSync) {
                Text(stringResource(R.string.home_sync_action))
            }
        }
    }
}
