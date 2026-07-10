package io.cloudcauldron.bocan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.pairing.PairingScreen
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

@Composable
private fun BocanApp(appGraph: AppGraph) {
    var showPairing by rememberSaveable { mutableStateOf(false) }
    if (showPairing) {
        val viewModel = remember { appGraph.pairingViewModel() }
        DisposableEffect(Unit) {
            onDispose { viewModel.dispose() }
        }
        PairingScreen(viewModel)
    } else {
        HomeScreen(onOpenPairing = { showPairing = true })
    }
}

@Composable
private fun HomeScreen(onOpenPairing: () -> Unit) {
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
        }
    }
}
