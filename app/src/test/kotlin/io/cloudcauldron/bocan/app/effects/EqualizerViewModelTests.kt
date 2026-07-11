package io.cloudcauldron.bocan.app.effects

import app.cash.turbine.test
import io.cloudcauldron.bocan.app.data.EqPreferencesSource
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.audio.BuiltInPresets
import io.cloudcauldron.bocan.playback.audio.EqState
import io.cloudcauldron.bocan.playback.audio.ReplayGainMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** An in-memory [EqPreferencesSource] that applies the transform to a held state. */
private class FakeEqPreferences : EqPreferencesSource {
    val flow = MutableStateFlow(EqState())
    override val state: Flow<EqState> get() = flow
    override suspend fun update(transform: (EqState) -> EqState) {
        flow.value = transform(flow.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerViewModelTests {
    private val preferences = FakeEqPreferences()

    private fun viewModel(): EqualizerViewModel {
        val d = UnconfinedTestDispatcher()
        return EqualizerViewModel(preferences, CoroutineDispatchers(io = d, default = d, main = d), newPresetId = { "uid-1" })
    }

    @Test
    fun `selecting a preset writes its curve`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.selectPreset(BuiltInPresets.rock)
            var state = awaitItem()
            while (state.activePresetId != BuiltInPresets.rock.id) state = awaitItem()
            assertEquals(BuiltInPresets.rock.bandGainsDb, state.bandGainsDb)
            cancelAndIgnoreRemainingEvents()
        }
        vm.dispose()
    }

    @Test
    fun `editing a band clears the active preset`() = runTest {
        val vm = viewModel()
        vm.selectPreset(BuiltInPresets.rock)
        vm.setBand(0, 9.0)
        assertNull(preferences.flow.value.activePresetId)
        assertEquals(9.0, preferences.flow.value.bandGainsDb[0])
    }

    @Test
    fun `saving then deleting a user preset round-trips`() = runTest {
        val vm = viewModel()
        vm.setBand(4, 3.0)
        vm.saveUserPreset("Night")
        assertEquals("uid-1", preferences.flow.value.activePresetId)
        assertEquals("Night", preferences.flow.value.userPresets.single().name)

        vm.deleteUserPreset("uid-1")
        assertTrue(preferences.flow.value.userPresets.isEmpty())
    }

    @Test
    fun `bass boost and preamp clamp to their ranges`() = runTest {
        val vm = viewModel()
        vm.setBassBoost(20.0)
        assertEquals(EqState.BASS_MAX_DB, preferences.flow.value.bassBoostDb)
        vm.setPreamp(-20.0)
        assertEquals(EqState.PREAMP_MIN_DB, preferences.flow.value.preampDb)
    }

    @Test
    fun `toggles and modes persist`() = runTest {
        val vm = viewModel()
        vm.setEnabled(true)
        vm.setReplayGainMode(ReplayGainMode.Album)
        vm.setSkipSilence(true)
        vm.setFadeSeconds(20)
        val state = preferences.flow.value
        assertTrue(state.enabled)
        assertEquals(ReplayGainMode.Album, state.replayGainMode)
        assertTrue(state.skipSilence)
        assertEquals(EqState.FADE_MAX_SECONDS, state.fadeSeconds)
        assertFalse(state.fadeSeconds == 20)
    }
}
