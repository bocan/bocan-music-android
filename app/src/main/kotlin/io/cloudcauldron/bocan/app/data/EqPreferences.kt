package io.cloudcauldron.bocan.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.playback.audio.EqState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.eqDataStore: DataStore<Preferences> by preferencesDataStore(name = "eq_prefs")

/** The effects settings the chain reads and the Equalizer screen edits; a fake stands in for tests. */
interface EqPreferencesSource {
    val state: Flow<EqState>

    suspend fun update(transform: (EqState) -> EqState)
}

/**
 * DataStore-backed effects settings. The whole [EqState] (EQ curve, bass boost,
 * ReplayGain mode and preamp, skip silence, fade window, and user presets) is stored as
 * one JSON string under a single key: the state is small, always read and written whole,
 * and user presets are a variable-length list, so one JSON blob is simpler and
 * migration-safe versus a key per field. A malformed or absent blob falls back to the
 * honest default [EqState] (everything off), never a crash.
 */
class EqPreferences(private val context: Context) : EqPreferencesSource {
    private val log = AppLog.forCategory(LogCategory.Playback)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val state: Flow<EqState> = context.eqDataStore.data.map { prefs ->
        prefs[STATE_KEY]?.let(::decode) ?: EqState()
    }

    override suspend fun update(transform: (EqState) -> EqState) {
        context.eqDataStore.edit { prefs ->
            val current = prefs[STATE_KEY]?.let(::decode) ?: EqState()
            prefs[STATE_KEY] = json.encodeToString(EqState.serializer(), transform(current))
        }
    }

    private fun decode(raw: String): EqState = try {
        json.decodeFromString(EqState.serializer(), raw)
    } catch (malformed: SerializationException) {
        // A corrupt blob must not brick effects: log and fall back to the off state.
        log.warning("eq.decode.failed", mapOf("error" to malformed.toString()))
        EqState()
    }

    private companion object {
        val STATE_KEY = stringPreferencesKey("eq_state_json")
    }
}
