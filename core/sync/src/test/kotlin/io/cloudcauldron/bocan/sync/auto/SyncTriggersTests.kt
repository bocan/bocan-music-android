package io.cloudcauldron.bocan.sync.auto

import io.cloudcauldron.bocan.sync.discovery.DiscoveredMac
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncTriggersTests {
    private val pairedFp = "ab".repeat(32)
    private val endpoint = MutableStateFlow<okhttp3.HttpUrl?>(null)
    private var triggers = 0
    private var clock = Instant.parse("2026-07-10T12:00:00Z")

    private fun mac(fingerprint: String = pairedFp, port: Int = 8443): DiscoveredMac = DiscoveredMac(
        serviceName = "Chris's Mac",
        host = InetAddress.getLoopbackAddress(),
        port = port,
        fingerprint = fingerprint,
        pairingMode = false,
        protocolVersion = 1
    )

    private fun triggers(pairedFingerprint: String? = pairedFp) = SyncTriggers(
        discovery = flowOf(emptyList()),
        pairedFingerprint = { pairedFingerprint },
        endpoint = endpoint,
        onPairedVisible = { triggers++ },
        debounce = Duration.ofMinutes(15),
        clock = { clock }
    )

    @Test
    fun `a paired mac appearing triggers exactly one sync and publishes its endpoint`() = runTest {
        val subject = triggers()

        subject.onDiscovery(listOf(mac()))
        subject.onDiscovery(listOf(mac())) // still within the debounce window

        assertEquals(1, triggers)
        assertEquals("https://127.0.0.1:8443/", endpoint.value.toString())
    }

    @Test
    fun `the debounce lifts after the window elapses`() = runTest {
        val subject = triggers()

        subject.onDiscovery(listOf(mac()))
        clock = clock.plus(Duration.ofMinutes(16))
        subject.onDiscovery(listOf(mac()))

        assertEquals(2, triggers)
    }

    @Test
    fun `an unpaired phone never triggers and clears the endpoint`() = runTest {
        endpoint.value = "https://127.0.0.1:1/".toHttpUrl()
        val subject = triggers(pairedFingerprint = null)

        subject.onDiscovery(listOf(mac()))

        assertEquals(0, triggers)
        assertNull(endpoint.value)
    }

    @Test
    fun `a different mac on the network is ignored`() = runTest {
        val subject = triggers()

        subject.onDiscovery(listOf(mac(fingerprint = "cd".repeat(32))))

        assertEquals(0, triggers)
        assertNull(endpoint.value)
    }
}
