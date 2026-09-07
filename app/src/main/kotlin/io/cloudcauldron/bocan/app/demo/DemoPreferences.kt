package io.cloudcauldron.bocan.app.demo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.demoDataStore: DataStore<Preferences> by preferencesDataStore(name = "demo_prefs")

/** Whether the demo library has had its one automatic seed; a fake stands in for tests. */
interface DemoPreferencesSource {
    val autoSeeded: Flow<Boolean>

    suspend fun setAutoSeeded()
}

/** DataStore-backed flag: set once after the first launch tries to seed, whatever the outcome. */
class DemoPreferences(private val context: Context) : DemoPreferencesSource {
    override val autoSeeded: Flow<Boolean> = context.demoDataStore.data.map { it[AUTO_SEEDED] ?: false }

    override suspend fun setAutoSeeded() {
        context.demoDataStore.edit { it[AUTO_SEEDED] = true }
    }

    private companion object {
        val AUTO_SEEDED = booleanPreferencesKey("auto_seeded")
    }
}
