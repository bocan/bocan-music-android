package io.cloudcauldron.bocan.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.scrobbleDataStore: DataStore<Preferences> by preferencesDataStore(name = "scrobble_prefs")

/** The scrobble on/off state: a master switch plus a per-provider enabled flag. */
data class ScrobbleToggles(val masterEnabled: Boolean = false, val enabledProviders: Set<String> = emptySet())

/** The scrobble toggles the service and settings read; a fake stands in for tests. */
interface ScrobbleSettingsSource {
    val toggles: Flow<ScrobbleToggles>

    suspend fun current(): ScrobbleToggles

    suspend fun setMasterEnabled(enabled: Boolean)

    suspend fun setProviderEnabled(providerId: String, enabled: Boolean)
}

/**
 * DataStore-backed scrobble toggles. The master switch gates everything; a provider
 * scrobbles only when the master is on and its own flag is set. Credentials live in the
 * encrypted TokenStore, never here.
 */
class ScrobbleSettings(private val context: Context) : ScrobbleSettingsSource {
    override val toggles: Flow<ScrobbleToggles> = context.scrobbleDataStore.data.map(::read)

    override suspend fun current(): ScrobbleToggles = toggles.first()

    override suspend fun setMasterEnabled(enabled: Boolean) {
        context.scrobbleDataStore.edit { it[MASTER] = enabled }
    }

    override suspend fun setProviderEnabled(providerId: String, enabled: Boolean) {
        context.scrobbleDataStore.edit { it[providerKey(providerId)] = enabled }
    }

    private fun read(prefs: Preferences): ScrobbleToggles {
        val master = prefs[MASTER] ?: false
        val enabled = prefs.asMap().keys
            .mapNotNull { key -> key.name.removePrefix(PROVIDER_PREFIX).takeIf { it != key.name } }
            .filter { prefs[providerKey(it)] == true }
            .toSet()
        return ScrobbleToggles(master, enabled)
    }

    private fun providerKey(providerId: String) = booleanPreferencesKey("$PROVIDER_PREFIX$providerId")

    private companion object {
        val MASTER = booleanPreferencesKey("master_enabled")
        const val PROVIDER_PREFIX = "provider_enabled_"
    }
}
