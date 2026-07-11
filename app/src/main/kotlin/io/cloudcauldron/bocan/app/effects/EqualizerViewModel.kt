package io.cloudcauldron.bocan.app.effects

import io.cloudcauldron.bocan.app.data.EqPreferencesSource
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.audio.EqPreset
import io.cloudcauldron.bocan.playback.audio.EqState
import io.cloudcauldron.bocan.playback.audio.ReplayGainMode
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the Equalizer screen. Every edit writes straight to [EqPreferencesSource]; the
 * app graph observes the same preferences and applies them to the effects chain, so a
 * change reaches the audio within a buffer (the master switch A/Bs instantly). The view
 * model holds no audio state of its own: the persisted [EqState] is the single source.
 */
// One setter per effects control is the screen's contract, not a decomposition smell.
@Suppress("TooManyFunctions")
class EqualizerViewModel(
    private val preferences: EqPreferencesSource,
    dispatchers: CoroutineDispatchers,
    private val newPresetId: () -> String = { "user." + UUID.randomUUID() }
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val state: StateFlow<EqState> =
        preferences.state.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), EqState())

    fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }

    fun setBand(index: Int, db: Double) = edit { it.withBand(index, db) }

    fun selectPreset(preset: EqPreset) = edit { it.withPreset(preset) }

    fun saveUserPreset(name: String) = edit { it.savingUserPreset(newPresetId(), name.trim()) }

    fun deleteUserPreset(id: String) = edit { it.deletingUserPreset(id) }

    fun setBassBoost(db: Double) = edit { it.copy(bassBoostDb = db.coerceIn(EqState.BASS_MIN_DB, EqState.BASS_MAX_DB)) }

    fun setReplayGainMode(mode: ReplayGainMode) = edit { it.copy(replayGainMode = mode) }

    fun setPreamp(db: Double) = edit { it.copy(preampDb = db.coerceIn(EqState.PREAMP_MIN_DB, EqState.PREAMP_MAX_DB)) }

    fun setSkipSilence(enabled: Boolean) = edit { it.copy(skipSilence = enabled) }

    fun setFadeSeconds(seconds: Int) = edit {
        it.copy(fadeSeconds = seconds.coerceIn(EqState.FADE_MIN_SECONDS, EqState.FADE_MAX_SECONDS))
    }

    fun dispose() = scope.cancel()

    private fun edit(transform: (EqState) -> EqState) {
        scope.launch { preferences.update(transform) }
    }

    companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
