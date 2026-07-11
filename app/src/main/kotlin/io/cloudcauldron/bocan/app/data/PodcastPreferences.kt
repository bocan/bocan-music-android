package io.cloudcauldron.bocan.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.podcastDataStore: DataStore<Preferences> by preferencesDataStore(name = "podcast_prefs")

/** The podcast preferences the player and settings read; a fake stands in for tests. */
interface PodcastPreferencesSource {
    val appDefaultSpeed: Flow<Double>
    val skipBackSeconds: Flow<Int>
    val skipForwardSeconds: Flow<Int>
    fun showSpeed(podcastId: Long): Flow<Double?>

    suspend fun setAppDefaultSpeed(speed: Double)
    suspend fun setSkipBackSeconds(seconds: Int)
    suspend fun setSkipForwardSeconds(seconds: Int)
    suspend fun setShowSpeed(podcastId: Long, speed: Double?)
}

/**
 * DataStore-backed podcast preferences: the app-wide default speed, skip-back and
 * skip-forward intervals, and per-show speed overrides keyed by podcastId. Per-show
 * overrides live here, never in the synced tables, so a sync can never overwrite them.
 */
class PodcastPreferences(private val context: Context) : PodcastPreferencesSource {
    override val appDefaultSpeed: Flow<Double> =
        context.podcastDataStore.data.map { it[APP_DEFAULT_SPEED] ?: DEFAULT_SPEED }

    override val skipBackSeconds: Flow<Int> =
        context.podcastDataStore.data.map { it[SKIP_BACK] ?: DEFAULT_SKIP_BACK }

    override val skipForwardSeconds: Flow<Int> =
        context.podcastDataStore.data.map { it[SKIP_FORWARD] ?: DEFAULT_SKIP_FORWARD }

    override fun showSpeed(podcastId: Long): Flow<Double?> = context.podcastDataStore.data.map { it[showSpeedKey(podcastId)] }

    override suspend fun setAppDefaultSpeed(speed: Double) = edit { it[APP_DEFAULT_SPEED] = speed }

    override suspend fun setSkipBackSeconds(seconds: Int) = edit { it[SKIP_BACK] = seconds }

    override suspend fun setSkipForwardSeconds(seconds: Int) = edit { it[SKIP_FORWARD] = seconds }

    override suspend fun setShowSpeed(podcastId: Long, speed: Double?) = edit { prefs ->
        if (speed == null) prefs.remove(showSpeedKey(podcastId)) else prefs[showSpeedKey(podcastId)] = speed
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.podcastDataStore.edit(block)
    }

    private fun showSpeedKey(podcastId: Long) = doublePreferencesKey("show_speed_$podcastId")

    private companion object {
        const val DEFAULT_SPEED = 1.0
        const val DEFAULT_SKIP_BACK = 15
        const val DEFAULT_SKIP_FORWARD = 30
        val APP_DEFAULT_SPEED = doublePreferencesKey("app_default_speed")
        val SKIP_BACK = intPreferencesKey("skip_back")
        val SKIP_FORWARD = intPreferencesKey("skip_forward")
    }
}
