package io.cloudcauldron.bocan.app.sync

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's auto-sync preferences, backed by plain SharedPreferences (these are
 * not secrets). Exposed as StateFlows so the settings screen, the discovery
 * trigger, and the worker scheduler observe the same source of truth.
 *
 * Sync-on-discovery and periodic sync are separate toggles; installs that carried
 * the older single auto-sync flag seed both from its value.
 */
class SyncSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val legacyAuto = prefs.getBoolean(KEY_LEGACY_AUTO, true)
    private val discovery = MutableStateFlow(prefs.getBoolean(KEY_DISCOVERY, legacyAuto))
    private val periodic = MutableStateFlow(prefs.getBoolean(KEY_PERIODIC, legacyAuto))
    private val charging = MutableStateFlow(prefs.getBoolean(KEY_CHARGING, false))

    /** Start a sync when the paired Mac appears on the network. */
    val syncOnDiscovery: StateFlow<Boolean> = discovery.asStateFlow()

    /** Run the periodic background sync worker. */
    val periodicSync: StateFlow<Boolean> = periodic.asStateFlow()

    /** Restrict the periodic worker to charging sessions. */
    val chargingOnly: StateFlow<Boolean> = charging.asStateFlow()

    fun setSyncOnDiscovery(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DISCOVERY, enabled) }
        discovery.value = enabled
    }

    fun setPeriodicSync(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PERIODIC, enabled) }
        periodic.value = enabled
    }

    fun setChargingOnly(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CHARGING, enabled) }
        charging.value = enabled
    }

    private companion object {
        const val PREFS = "bocan.sync.settings"
        const val KEY_LEGACY_AUTO = "autoSync"
        const val KEY_DISCOVERY = "syncOnDiscovery"
        const val KEY_PERIODIC = "periodicSync"
        const val KEY_CHARGING = "chargingOnly"
    }
}
