package io.cloudcauldron.bocan.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.media3.common.util.UnstableApi

/** The launcher's entry point to the Bòcan widget. */
@UnstableApi
class BocanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BocanWidget()
}
