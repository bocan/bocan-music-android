package io.cloudcauldron.bocan.app.settings

import app.cash.turbine.test
import io.cloudcauldron.bocan.app.podcasts.FakePodcastPreferences
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastSettingsViewModelTests {
    private val preferences = FakePodcastPreferences()

    private fun viewModel(): PodcastSettingsViewModel {
        val d = UnconfinedTestDispatcher()
        return PodcastSettingsViewModel(preferences, CoroutineDispatchers(io = d, default = d, main = d))
    }

    @Test
    fun `setters write straight to the podcast preferences`() = runTest {
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            vm.setDefaultSpeed(1.5)
            vm.setSkipBackSeconds(45)
            vm.setSkipForwardSeconds(60)

            var state = awaitItem()
            while (state.defaultSpeed != 1.5 || state.skipBackSeconds != 45 || state.skipForwardSeconds != 60) {
                state = awaitItem()
            }
            assertEquals(1.5, state.defaultSpeed)
            assertEquals(45, state.skipBackSeconds)
            assertEquals(60, state.skipForwardSeconds)
            cancelAndIgnoreRemainingEvents()
        }
        vm.dispose()
    }
}
