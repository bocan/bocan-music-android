package io.cloudcauldron.bocan.app.player

import android.content.Context
import android.graphics.BitmapFactory
import android.os.PowerManager
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val paletteCache = LruCache<String, Int>(32)

/**
 * A subtle ambient wash behind Now Playing, derived from the artwork's palette on a
 * background dispatcher and cached per artwork Uri. Disabled under battery saver, and
 * the colour transition snaps rather than animates under reduced motion. The alpha is
 * capped low enough that onSurface and onSurfaceVariant text drawn over the washed
 * background keeps a 4.5:1 contrast floor whatever the artwork supplies.
 */
@Composable
fun Modifier.ambientBackground(artworkUri: String?): Modifier {
    val context = LocalContext.current
    var ambient by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(artworkUri) {
        ambient = artworkUri?.let { extractAmbient(context, it)?.let(::Color) }
    }
    val reducedMotion = isReducedMotion(context)
    val target = ambient ?: Color.Transparent
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = if (reducedMotion) snap() else tween(AMBIENT_ANIM_MS),
        label = "ambient"
    )
    return this.background(
        Brush.verticalGradient(listOf(animated.copy(alpha = AMBIENT_ALPHA), Color.Transparent))
    )
}

private suspend fun extractAmbient(context: Context, artworkUri: String): Int? = withContext(Dispatchers.IO) {
    paletteCache.get(artworkUri)?.let { return@withContext it }
    val powerManager = context.getSystemService<PowerManager>()
    if (powerManager?.isPowerSaveMode == true) return@withContext null
    val path = artworkUri.toUri().path ?: return@withContext null
    val options = BitmapFactory.Options().apply { inSampleSize = SAMPLE_SIZE }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return@withContext null
    val swatch = Palette.from(bitmap).maximumColorCount(MAX_COLORS).generate().run {
        darkMutedSwatch ?: dominantSwatch
    }
    bitmap.recycle()
    swatch?.rgb?.also { paletteCache.put(artworkUri, it) }
}

private const val AMBIENT_ALPHA = 0.3f
private const val AMBIENT_ANIM_MS = 600
private const val SAMPLE_SIZE = 4
private const val MAX_COLORS = 16
