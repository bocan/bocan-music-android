package io.cloudcauldron.bocan.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Resolves a content-addressed artwork hash to a Coil model (a local File) or null
 * when the artwork is not on disk. Provided once at the app root so cells and rows do
 * not each need the ArtworkStore threaded through them.
 */
val LocalArtworkResolver = staticCompositionLocalOf<(String?) -> Any?> { { null } }

/**
 * Square artwork for an [artworkHash], loaded from local storage via Coil with a
 * crossfade, falling back to a music-note placeholder when absent. Coil sizes the
 * decode to the composable's bounds, so a grid of thumbnails never decodes full-size
 * art. Marked decorative for TalkBack: the surrounding row or cell carries the label.
 */
@Composable
fun ArtworkImage(artworkHash: String?, modifier: Modifier = Modifier) {
    val model = LocalArtworkResolver.current(artworkHash)
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        if (model == null) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA)
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private const val PLACEHOLDER_ALPHA = 0.5f
