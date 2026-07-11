package io.cloudcauldron.bocan.app.settings

import io.cloudcauldron.bocan.app.data.PodcastPreferencesSource
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The podcast settings the screen renders: app-wide default speed and skip intervals. */
data class PodcastSettingsUiState(
    val defaultSpeed: Double = DEFAULT_SPEED,
    val skipBackSeconds: Int = DEFAULT_SKIP_BACK,
    val skipForwardSeconds: Int = DEFAULT_SKIP_FORWARD
) {
    private companion object {
        const val DEFAULT_SPEED = 1.0
        const val DEFAULT_SKIP_BACK = 15
        const val DEFAULT_SKIP_FORWARD = 30
    }
}

/**
 * Drives the Podcasts section of settings: the app-wide default speed and the skip-back
 * and skip-forward intervals. Every setter writes straight to the podcast preferences,
 * which never touch the synced tables.
 */
class PodcastSettingsViewModel(private val preferences: PodcastPreferencesSource, dispatchers: CoroutineDispatchers) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val state: StateFlow<PodcastSettingsUiState> =
        combine(
            preferences.appDefaultSpeed,
            preferences.skipBackSeconds,
            preferences.skipForwardSeconds
        ) { speed, back, forward ->
            PodcastSettingsUiState(speed, back, forward)
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), PodcastSettingsUiState())

    fun setDefaultSpeed(speed: Double) = launch { preferences.setAppDefaultSpeed(speed) }

    fun setSkipBackSeconds(seconds: Int) = launch { preferences.setSkipBackSeconds(seconds) }

    fun setSkipForwardSeconds(seconds: Int) = launch { preferences.setSkipForwardSeconds(seconds) }

    fun dispose() = scope.cancel()

    private fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        val SPEED_PRESETS = listOf(0.8, 1.0, 1.2, 1.5, 2.0)
        val SKIP_PRESETS = listOf(10, 15, 30, 45, 60)
    }
}
