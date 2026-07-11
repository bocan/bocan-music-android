package io.cloudcauldron.bocan.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.cloudcauldron.bocan.app.R

/**
 * Settings placeholder for phase 05: it hosts the pairing and library-sync entry points
 * so both stay reachable now that the home screen is the library. Later phases fill in
 * playback, EQ, and scrobble settings here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenPairing: () -> Unit, onOpenSync: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.home_pair_action)) },
                leadingContent = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPairing)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.home_sync_action)) },
                leadingContent = { Icon(Icons.Rounded.Sync, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSync)
            )
        }
    }
}
