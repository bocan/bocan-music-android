package io.cloudcauldron.bocan.sync.discovery

import app.cash.turbine.test
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import java.net.InetAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacDiscoveryTests {
    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private fun resolved(name: String, fp: String? = "ab".repeat(32), version: String? = "1", pairingMode: String? = "0"): ResolvedService =
        ResolvedService(
            serviceName = name,
            host = loopback,
            port = 8443,
            txt = buildMap {
                if (fp != null) put("fp", fp)
                if (version != null) put("v", version)
                if (pairingMode != null) put("pm", pairingMode)
            }
        )

    // Extension on TestScope so the injected dispatcher shares runTest's scheduler;
    // an unlinked TestDispatcher would never be advanced and flowOn would deadlock.
    private fun TestScope.discovery(flow: Flow<List<ResolvedService>>, lease: WifiMulticastLease) = MacDiscovery(
        browser = object : NsdServiceBrowser {
            override fun services() = flow
        },
        multicastLease = lease,
        dispatchers = CoroutineDispatchers(io = UnconfinedTestDispatcher(testScheduler))
    )

    @Test
    fun `parses valid adverts and drops malformed ones`() = runTest {
        val services = listOf(
            resolved("Mac A", pairingMode = "1"),
            resolved("Mac B", fp = null), // missing fingerprint
            resolved("Mac C", version = null), // missing version
            resolved("Mac D", fp = "not-hex") // malformed fingerprint
        )
        val lease = RecordingLease()
        discovery(flowOf(services), lease).discover().test {
            val macs = awaitItem()
            assertEquals(listOf("Mac A"), macs.map { it.serviceName })
            awaitComplete()
        }
    }

    @Test
    fun `reads pairing mode, version, and normalises fingerprint casing`() = runTest {
        val service = resolved("Chris's Mac", fp = "AB".repeat(32), version = "1", pairingMode = "1")
        discovery(flowOf(listOf(service)), RecordingLease()).discover().test {
            val mac = awaitItem().single()
            assertTrue(mac.pairingMode)
            assertEquals(1, mac.protocolVersion)
            assertEquals("ab".repeat(32), mac.fingerprint)
            assertEquals(8443, mac.port)
            awaitComplete()
        }
    }

    @Test
    fun `pairing mode is false when pm is absent or not one`() = runTest {
        val services = listOf(resolved("A", pairingMode = null), resolved("B", pairingMode = "0"))
        discovery(flowOf(services), RecordingLease()).discover().test {
            assertTrue(awaitItem().none { it.pairingMode })
            awaitComplete()
        }
    }

    @Test
    fun `holds the multicast lease for the lifetime of a collection`() = runTest {
        val lease = RecordingLease()
        discovery(flowOf(listOf(resolved("A"))), lease).discover().test {
            awaitItem()
            awaitComplete()
        }
        assertEquals(1, lease.acquired)
        assertEquals(1, lease.released)
    }

    @Test
    fun `discovered macs are ordered by service name`() = runTest {
        val services = listOf(resolved("Zeta"), resolved("Alpha"), resolved("Mike"))
        discovery(flowOf(services), RecordingLease()).discover().test {
            assertEquals(listOf("Alpha", "Mike", "Zeta"), awaitItem().map { it.serviceName })
            awaitComplete()
        }
    }

    private class RecordingLease : WifiMulticastLease {
        var acquired = 0
        var released = 0

        override fun acquire() {
            acquired++
        }

        override fun release() {
            released++
        }
    }
}
