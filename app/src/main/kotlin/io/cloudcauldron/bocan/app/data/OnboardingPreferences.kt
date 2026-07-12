package io.cloudcauldron.bocan.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding_prefs")

/** Whether the first-run flow has been completed or skipped; a fake stands in for tests. */
interface OnboardingPreferencesSource {
    val completed: Flow<Boolean>

    suspend fun setCompleted()
}

/** DataStore-backed onboarding flag: set once when the welcome flow finishes or is skipped. */
class OnboardingPreferences(private val context: Context) : OnboardingPreferencesSource {
    override val completed: Flow<Boolean> = context.onboardingDataStore.data.map { it[COMPLETED] ?: false }

    override suspend fun setCompleted() {
        context.onboardingDataStore.edit { it[COMPLETED] = true }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("completed")
    }
}
