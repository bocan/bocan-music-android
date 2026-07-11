package io.cloudcauldron.bocan.app.sync

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's auto-sync preferences, backed by plain SharedPreferences (these are
 * not secrets). Exposed as StateFlows so the status screen and the discovery
 * trigger observe the same source of truth.
 */
class SyncSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val autoSync = MutableStateFlow(prefs.getBoolean(KEY_AUTO, true))
    private val charging = MutableStateFlow(prefs.getBoolean(KEY_CHARGING, false))

    val autoSyncEnabled: StateFlow<Boolean> = autoSync.asStateFlow()
    val chargingOnly: StateFlow<Boolean> = charging.asStateFlow()

    fun setAutoSync(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO, enabled) }
        autoSync.value = enabled
    }

    fun setChargingOnly(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CHARGING, enabled) }
        charging.value = enabled
    }

    private companion object {
        const val PREFS = "bocan.sync.settings"
        const val KEY_AUTO = "autoSync"
        const val KEY_CHARGING = "chargingOnly"
    }
}
