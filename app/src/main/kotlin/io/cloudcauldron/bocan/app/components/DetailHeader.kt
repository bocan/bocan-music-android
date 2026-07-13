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
import androidx.compose.material3.IconButton
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

/**
 * A top-bar action that shuffles all of [trackIds] and starts playing. Rendered as an
 * icon-only button, so it carries its own content description; it hides itself when there
 * is nothing to play.
 */
@Composable
fun ShuffleAllAction(trackIds: List<Long>, onShuffle: (List<Long>) -> Unit) {
    if (trackIds.isEmpty()) return
    IconButton(onClick = { onShuffle(trackIds) }) {
        Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.action_shuffle_all))
    }
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
