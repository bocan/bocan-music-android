package io.cloudcauldron.bocan.app.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.app.data.ScrobbleSettingsSource
import io.cloudcauldron.bocan.app.data.ScrobbleToggles
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.scrobble.AuthState
import io.cloudcauldron.bocan.scrobble.CoroutineDispatchers
import io.cloudcauldron.bocan.scrobble.PlayEvent
import io.cloudcauldron.bocan.scrobble.SubmissionResult
import io.cloudcauldron.bocan.scrobble.auth.TokenKeys
import io.cloudcauldron.bocan.scrobble.auth.TokenStore
import io.cloudcauldron.bocan.scrobble.providers.ProviderId
import io.cloudcauldron.bocan.scrobble.providers.ScrobbleProvider
import io.cloudcauldron.bocan.scrobble.queue.ScrobbleQueue
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ScrobbleSettingsViewModelTests {
    private val settings = FakeScrobbleSettings()
    private val tokens = FakeTokens()

    private fun runVmTest(block: suspend (ScrobbleSettingsViewModel) -> Unit) = runTest {
        val db = BocanDatabase.createInMemory(ApplicationProvider.getApplicationContext(), UnconfinedTestDispatcher(testScheduler))
        try {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val queue = ScrobbleQueue(db.scrobbleDao(), CoroutineDispatchers(io = dispatcher, default = dispatcher))
            val providers = listOf(FakeAppProvider(ProviderId.LISTENBRAINZ, "ListenBrainz", connected = true))
            val vm =
                ScrobbleSettingsViewModel(
                    providers,
                    settings,
                    queue,
                    tokens,
                    lastFm = null,
                    CoroutineDispatchers(io = dispatcher, default = dispatcher)
                )
            block(vm)
            vm.dispose()
        } finally {
            db.close()
        }
    }

    @Test
    fun `the master toggle writes to settings`() = runVmTest { vm ->
        vm.setMasterEnabled(true)
        assertTrue(settings.current().masterEnabled)
    }

    @Test
    fun `enabling a provider writes to settings`() = runVmTest { vm ->
        vm.setProviderEnabled(ProviderId.LISTENBRAINZ, true)
        assertTrue(ProviderId.LISTENBRAINZ in settings.current().enabledProviders)
    }

    @Test
    fun `pasting a token stores it under the provider key`() = runVmTest { vm ->
        vm.connectWithToken(ProviderId.LISTENBRAINZ, "  MY_TOKEN  ")
        assertEquals("MY_TOKEN", tokens.get(TokenKeys.LISTENBRAINZ_TOKEN))
    }

    @Test
    fun `state reflects a connected provider`() = runVmTest { vm ->
        vm.state.test {
            var state = awaitItem()
            while (state.providers.isEmpty()) state = awaitItem()
            val row = state.providers.single()
            assertEquals(ProviderId.LISTENBRAINZ, row.id)
            assertTrue(row.connected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeScrobbleSettings : ScrobbleSettingsSource {
        private val flow = MutableStateFlow(ScrobbleToggles())
        override val toggles: Flow<ScrobbleToggles> = flow.asStateFlow()
        override suspend fun current(): ScrobbleToggles = flow.value
        override suspend fun setMasterEnabled(enabled: Boolean) {
            flow.value = flow.value.copy(masterEnabled = enabled)
        }

        override suspend fun setProviderEnabled(providerId: String, enabled: Boolean) {
            val updated = if (enabled) flow.value.enabledProviders + providerId else flow.value.enabledProviders - providerId
            flow.value = flow.value.copy(enabledProviders = updated)
        }
    }

    private class FakeTokens : TokenStore {
        private val values = ConcurrentHashMap<String, String>()
        override fun observe(key: String): Flow<String?> = MutableStateFlow(values[key]).asStateFlow()
        override suspend fun get(key: String): String? = values[key]
        override suspend fun set(key: String, value: String) {
            values[key] = value
        }

        override suspend fun clear(key: String) {
            values.remove(key)
        }
    }

    private class FakeAppProvider(override val id: String, override val displayName: String, connected: Boolean) : ScrobbleProvider {
        override val authState: Flow<AuthState> =
            MutableStateFlow(if (connected) AuthState.Connected("tester") else AuthState.Disconnected).asStateFlow()

        override suspend fun isAuthenticated(): Boolean = true
        override suspend fun updateNowPlaying(play: PlayEvent) = Unit
        override suspend fun scrobble(batch: List<PlayEvent>): List<SubmissionResult> = emptyList()
    }
}
