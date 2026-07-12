package io.cloudcauldron.bocan.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.common.util.UnstableApi
import io.cloudcauldron.bocan.app.MainActivity
import io.cloudcauldron.bocan.app.R

/**
 * The Bòcan home-screen widget, built with Glance primitives only (no arbitrary
 * composables). It renders from the persisted [WidgetState] so it survives a launcher or
 * process restart, resizes across the 4x1 to 4x2 range, deep-links into Now Playing when
 * tapped, and drives play/pause and next (skip-forward for a podcast) through the session
 * via [WidgetControlCallback].
 */
@UnstableApi
class BocanWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = WidgetStateStore(context).read()
        provideContent { GlanceTheme { WidgetBody(state) } }
    }

    private fun openNowPlaying(context: Context): Intent = Intent(Intent.ACTION_VIEW, DEEP_LINK.toUri(), context, MainActivity::class.java)

    @androidx.compose.runtime.Composable
    private fun WidgetBody(state: WidgetState) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp)
                .clickable(actionStartActivity(openNowPlaying(context))),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.hasContent) state.title else context.getString(R.string.widget_nothing_playing),
                style = TextStyle(color = GlanceTheme.colors.onSurface),
                maxLines = 1
            )
            if (state.hasContent && state.subtitle.isNotEmpty()) {
                Text(text = state.subtitle, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
            }
            if (state.hasContent) {
                Row(modifier = GlanceModifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ControlButton(
                        icon = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        description = context.getString(if (state.isPlaying) R.string.widget_pause else R.string.widget_play),
                        action = WidgetControlCallback.ACTION_PLAY_PAUSE
                    )
                    ControlButton(
                        icon = if (state.isPodcast) android.R.drawable.ic_media_ff else android.R.drawable.ic_media_next,
                        description = context.getString(if (state.isPodcast) R.string.widget_skip_forward else R.string.widget_next),
                        action = if (state.isPodcast) WidgetControlCallback.ACTION_SKIP_FORWARD else WidgetControlCallback.ACTION_NEXT
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ControlButton(icon: Int, description: String, action: String) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            // Platform media drawables are a fixed light gray; tint them so they
            // follow the widget theme in both day and night.
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.size(48.dp).padding(8.dp).clickable(
                actionRunCallback<WidgetControlCallback>(
                    actionParametersOf(WidgetControlCallback.ACTION_KEY to action)
                )
            )
        )
    }

    private companion object {
        const val DEEP_LINK = "bocan://nowplaying"
    }
}
