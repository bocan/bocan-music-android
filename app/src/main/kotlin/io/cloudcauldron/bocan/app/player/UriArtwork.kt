package io.cloudcauldron.bocan.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Artwork loaded from a resolved file Uri string (as carried on the player state), with
 * a music-note placeholder. Decorative for TalkBack; the surrounding screen has the label.
 */
@Composable
fun UriArtwork(uri: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        if (uri == null) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current).data(uri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
