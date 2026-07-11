package io.cloudcauldron.bocan.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R

/** Large square artwork for a detail header. */
@Composable
fun DetailArtwork(artworkHash: String?, modifier: Modifier = Modifier) {
    ArtworkImage(
        artworkHash = artworkHash,
        modifier = modifier
            .size(200.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

/** The Play and Shuffle buttons shown under a detail header. */
@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Text(stringResource(R.string.action_play), modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null)
            Text(stringResource(R.string.action_shuffle), modifier = Modifier.padding(start = 6.dp))
        }
    }
}
