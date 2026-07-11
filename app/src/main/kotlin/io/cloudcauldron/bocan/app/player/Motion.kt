package io.cloudcauldron.bocan.app.player

import android.content.Context
import android.provider.Settings

/**
 * True when the system's animation scale is zeroed (the "remove animations" a11y
 * setting). Callers jump instead of animating: ambient background transitions and
 * lyrics auto-scroll honour this.
 */
fun isReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
