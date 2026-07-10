package io.cloudcauldron.bocan.sync.pairing

import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.discovery.DiscoveredMac
import io.cloudcauldron.bocan.sync.heldCertificate
import io.cloudcauldron.bocan.sync.heldIdentity
import io.cloudcauldron.bocan.sync.identity.Fingerprints
import io.cloudcauldron.bocan.sync.net.PairedServerStore
import io.cloudcauldron.bocan.sync.net.SyncHttpClientFactory
import io.cloudcauldron.bocan.sync.tlsMockWebServer
import java.net.InetAddress
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * The pairing ceremony against a TLS MockWebServer with mutual auth. Plain JUnit
 * (not Robolectric): Robolectric does not populate OkHttp's client-side
 * response.handshake, and the security property here is exactly that the pinned
 * fingerprint comes from that TLS handshake.
 */
class PairingClientTests {
    private val phone = heldIdentity()
    private val macCert = heldCertificate("bocan-mac-11112222")
    private val macFingerprint = Fingerprints.ofCertificate(macCert.certificate)
    private val phoneNonce = ByteArray(32) { 7 }
    private val macNonce = ByteArray(32) { 9 }

    private lateinit var server: MockWebServer
    private lateinit var store: FakeServerStore
    private lateinit var client: PairingClient

    @Before
    fun setUp() {
        server = tlsMockWebServer(macCert, phone.certificate)
        server.start()
        store = FakeServerStore()
        client = PairingClient(
            identity = phone,
            clientFactory = SyncHttpClientFactory(phone),
            trustStore = store,
            deviceName = "Chris's Pixel",
            dispatchers = CoroutineDispatchers(io = Dispatchers.IO),
            randomBytes = { size -> phoneNonce.copyOf(size) }
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `happy path persists the pinned relationship from the TLS certificate`() = runTest {
        enqueueStart(sessionId = "session-1", serverName = "Chris's MacBook")
        enqueueConfirm(serverId = "server-uuid")

        client.start(discoveredMac())
        val awaiting = client.state.value as PairingState.AwaitingCode
        assertEquals(expectedCode(macFingerprint), awaiting.expectedCode)

        client.submitCode(awaiting.expectedCode)

        assertEquals(PairingState.Paired("Chris's MacBook"), client.state.value)
        val saved = requireNotNull(store.latest())
        assertEquals("server-uuid", saved.serverId)
        assertEquals(macFingerprint, saved.certFingerprint)
        assertArrayEquals(macCert.certificate.encoded, saved.certDer)

        val startBody = SyncJson.decodeFromString<PairStartRequest>(requireNotNull(server.takeRequest().body).utf8())
        assertEquals("Chris's Pixel", startBody.deviceName)
        assertEquals(base64(phoneNonce), startBody.noncePhone)
        val confirmBody = SyncJson.decodeFromString<PairConfirmRequest>(requireNotNull(server.takeRequest().body).utf8())
        assertEquals(base64(PairingCode.confirmProof(awaiting.expectedCode, "session-1")), confirmBody.proof)
    }

    @Test
    fun `a wrong code never sends confirm and does not persist`() = runTest {
        enqueueStart()
        client.start(discoveredMac())
        val expected = (client.state.value as PairingState.AwaitingCode).expectedCode

        client.submitCode(wrongCode(expected))

        assertEquals(PairingState.Failed(SyncError.CodeMismatch), client.state.value)
        assertEquals(1, server.requestCount)
        assertNull(store.latest())
    }

    @Test
    fun `three wrong entries abandon the session`() = runTest {
        enqueueStart()
        client.start(discoveredMac())
        val wrong = wrongCode((client.state.value as PairingState.AwaitingCode).expectedCode)

        client.submitCode(wrong)
        assertEquals(PairingState.Failed(SyncError.CodeMismatch), client.state.value)
        client.submitCode(wrong)
        assertEquals(PairingState.Failed(SyncError.CodeMismatch), client.state.value)
        client.submitCode(wrong)
        assertEquals(PairingState.Failed(SyncError.TooManyAttempts), client.state.value)
        assertEquals(1, server.requestCount)
        assertNull(store.latest())
    }

    @Test
    fun `a MITM presenting a different certificate yields a different code and aborts`() = runTest {
        enqueueStart()
        client.start(discoveredMac())
        val phoneComputed = (client.state.value as PairingState.AwaitingCode).expectedCode

        // The code an honest Mac holding a different certificate would show.
        val otherMacFingerprint = Fingerprints.ofCertificate(heldCertificate("bocan-mac-cafebabe").certificate)
        val macScreenCode = expectedCode(otherMacFingerprint)
        assertNotEquals("substituting the certificate must change the code", phoneComputed, macScreenCode)

        client.submitCode(macScreenCode)

        assertEquals(PairingState.Failed(SyncError.CodeMismatch), client.state.value)
        assertEquals(1, server.requestCount)
        assertNull(store.latest())
    }

    @Test
    fun `the pairing client refuses a server whose certificate does not match the TXT fingerprint`() = runTest {
        val wrongFingerprint = Fingerprints.ofCertificate(heldCertificate("bocan-mac-99998888").certificate)

        client.start(discoveredMac(fingerprint = wrongFingerprint))

        val state = client.state.value as PairingState.Failed
        assertTrue(state.error is SyncError.CertificatePinMismatch)
        assertEquals(0, server.requestCount)
        assertNull(store.latest())
    }

    @Test
    fun `a badProof response fails with the typed error`() = runTest {
        enqueueStart()
        enqueueError(code = 400, machineCode = "badProof")
        client.start(discoveredMac())
        val expected = (client.state.value as PairingState.AwaitingCode).expectedCode

        client.submitCode(expected)

        assertEquals(PairingState.Failed(SyncError.BadProof), client.state.value)
        assertNull(store.latest())
    }

    @Test
    fun `a pairingExpired response on start fails with the typed error`() = runTest {
        enqueueError(code = 410, machineCode = "pairingExpired")
        client.start(discoveredMac())
        assertEquals(PairingState.Failed(SyncError.PairingExpired), client.state.value)
    }

    @Test
    fun `a busy response carries the retry-after seconds`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(503)
                .addHeader("Retry-After", "5")
                .body(SyncJson.encodeToString(ErrorBody("busy", "scan in progress")))
                .build()
        )
        client.start(discoveredMac())
        assertEquals(PairingState.Failed(SyncError.ServerBusy(5)), client.state.value)
    }

    @Test
    fun `an advertised protocol version beyond this client is refused`() = runTest {
        client.start(discoveredMac(protocolVersion = 2))
        val failed = client.state.value as PairingState.Failed
        assertTrue(failed.error is SyncError.UnsupportedProtocol)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the code and proof never appear in logs`() = runTest {
        val tree = RecordingTree()
        Timber.plant(tree)
        try {
            enqueueStart(sessionId = "session-1")
            enqueueConfirm()
            client.start(discoveredMac())
            val code = (client.state.value as PairingState.AwaitingCode).expectedCode
            client.submitCode(code)
            val proof = base64(PairingCode.confirmProof(code, "session-1"))

            assertTrue(tree.messages.isNotEmpty())
            assertTrue("raw code leaked", tree.messages.none { it.contains(code) })
            assertTrue("raw proof leaked", tree.messages.none { it.contains(proof) })
            assertTrue(tree.messages.any { it.contains("code=<redacted>") })
            assertTrue(tree.messages.any { it.contains("proof=<redacted>") })
        } finally {
            Timber.uprootAll()
        }
    }

    private fun discoveredMac(port: Int = server.port, fingerprint: String = macFingerprint, protocolVersion: Int = 1): DiscoveredMac =
        DiscoveredMac(
            serviceName = "Chris's MacBook",
            host = InetAddress.getLoopbackAddress(),
            port = port,
            fingerprint = fingerprint,
            pairingMode = true,
            protocolVersion = protocolVersion
        )

    private fun enqueueStart(sessionId: String = "session-1", serverName: String = "Chris's MacBook") {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(SyncJson.encodeToString(PairStartResponse(1, serverName, base64(macNonce), sessionId)))
                .build()
        )
    }

    private fun enqueueConfirm(status: String = "paired", serverId: String = "server-uuid") {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(SyncJson.encodeToString(PairConfirmResponse(status, serverId)))
                .build()
        )
    }

    private fun enqueueError(code: Int, machineCode: String) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .body(SyncJson.encodeToString(ErrorBody(machineCode, "denied")))
                .build()
        )
    }

    private fun expectedCode(fingerprintMac: String): String = PairingCode.derive(fingerprintMac, phone.fingerprint, phoneNonce, macNonce)

    private fun wrongCode(expected: String): String {
        val next = (expected.toInt() + 1) % 1_000_000
        return next.toString().padStart(6, '0')
    }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private class FakeServerStore : PairedServerStore {
        private val saved = mutableListOf<SyncServerEntity>()

        override suspend fun save(server: SyncServerEntity) {
            saved.add(server)
        }

        fun latest(): SyncServerEntity? = saved.lastOrNull()
    }

    private class RecordingTree : Timber.Tree() {
        val messages = mutableListOf<String>()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            messages.add(message)
        }
    }
}
