package io.cloudcauldron.bocan.app.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.sync.engine.SyncState

/**
 * A slim banner for the library screen while a sync is transferring. It renders
 * nothing at all when the engine is idle or between transfers, so it costs a
 * caller only a `when` on the state.
 */
@Composable
fun SyncBanner(state: SyncState, modifier: Modifier = Modifier) {
    val text = when (state) {
        is SyncState.Transferring -> stringResource(R.string.sync_banner_transferring, state.filesDone, state.filesTotal)
        is SyncState.CheckingManifest -> stringResource(R.string.sync_state_checking)
        is SyncState.Applying -> stringResource(R.string.sync_state_applying)
        else -> return
    }
    Surface(tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clearAndSetSemantics { contentDescription = text }
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
