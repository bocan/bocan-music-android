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

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_prefs")

/** Playback-behaviour preferences the app and settings read; a fake stands in for tests. */
interface PlaybackPreferencesSource {
    /** Whether playback resumes when headphones reconnect after being unplugged. Off by default. */
    val resumeOnReconnect: Flow<Boolean>

    suspend fun resumeOnReconnectNow(): Boolean

    suspend fun setResumeOnReconnect(enabled: Boolean)
}

/**
 * DataStore-backed playback preferences. The one setting so far is resume-on-reconnect: it
 * defaults off because auto-resuming a car or headset is a surprise more often than a
 * convenience, and it is gated by the foreground-start rules when it does fire.
 */
class PlaybackPreferences(private val context: Context) : PlaybackPreferencesSource {
    override val resumeOnReconnect: Flow<Boolean> = context.playbackDataStore.data.map { it[RESUME_ON_RECONNECT] ?: false }

    override suspend fun resumeOnReconnectNow(): Boolean = resumeOnReconnect.first()

    override suspend fun setResumeOnReconnect(enabled: Boolean) {
        context.playbackDataStore.edit { it[RESUME_ON_RECONNECT] = enabled }
    }

    private companion object {
        val RESUME_ON_RECONNECT = booleanPreferencesKey("resume_on_reconnect")
    }
}
