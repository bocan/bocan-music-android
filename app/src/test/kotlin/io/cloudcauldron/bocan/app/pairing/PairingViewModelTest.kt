package io.cloudcauldron.bocan.app.pairing

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.discovery.MacDiscovery
import io.cloudcauldron.bocan.sync.discovery.NsdServiceBrowser
import io.cloudcauldron.bocan.sync.discovery.ResolvedService
import io.cloudcauldron.bocan.sync.discovery.WifiMulticastLease
import io.cloudcauldron.bocan.sync.identity.DeviceIdentity
import io.cloudcauldron.bocan.sync.net.SyncHttpClientFactory
import io.cloudcauldron.bocan.sync.net.TrustStore
import io.cloudcauldron.bocan.sync.pairing.PairingClient
import io.cloudcauldron.bocan.sync.pairing.PairingState
import java.net.InetAddress
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PairingViewModelTest {
    private val db = BocanDatabase.createInMemory(ApplicationProvider.getApplicationContext(), Dispatchers.IO)

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `ui state exposes only macs in pairing mode and starts discovering`() = runTest {
        val services = listOf(
            resolvedService("Ready Mac", pairingMode = "1"),
            resolvedService("Idle Mac", pairingMode = "0")
        )
        val viewModel = pairingViewModel(flowOf(services))

        viewModel.state.test {
            // The initial empty snapshot may arrive before discovery resolves; wait it out.
            var state = awaitItem()
            while (state.pairableMacs.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(listOf("Ready Mac"), state.pairableMacs.map { it.serviceName })
            assertEquals(PairingState.Discovering, state.pairing)
            assertFalse(state.busy)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.dispose()
    }

    private fun pairingViewModel(services: Flow<List<ResolvedService>>): PairingViewModel {
        val dispatchers = CoroutineDispatchers(io = Dispatchers.IO, default = UnconfinedTestDispatcher())
        val discovery = MacDiscovery(
            browser = object : NsdServiceBrowser {
                override fun services() = services
            },
            multicastLease = object : WifiMulticastLease {
                override fun acquire() = Unit
                override fun release() = Unit
            },
            dispatchers = dispatchers
        )
        val pairingClient = PairingClient(
            identity = FakeIdentity,
            clientFactory = SyncHttpClientFactory(FakeIdentity),
            trustStore = TrustStore(db.syncDao()),
            deviceName = "Test Phone",
            dispatchers = dispatchers
        )
        return PairingViewModel(discovery, pairingClient, dispatchers)
    }

    private fun resolvedService(name: String, pairingMode: String): ResolvedService = ResolvedService(
        serviceName = name,
        host = InetAddress.getLoopbackAddress(),
        port = 8443,
        txt = mapOf("fp" to "ab".repeat(32), "v" to "1", "pm" to pairingMode)
    )

    /** The identity is never exercised in these tests; the certificate is unused. */
    private object FakeIdentity : DeviceIdentity {
        override val certificate: X509Certificate get() = error("certificate unused in view model tests")
        override val fingerprint: String = "cd".repeat(32)
        override fun keyManagers(): Array<KeyManager> = emptyArray()
    }
}
