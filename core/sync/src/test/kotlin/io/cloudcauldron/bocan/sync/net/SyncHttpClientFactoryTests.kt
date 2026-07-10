package io.cloudcauldron.bocan.sync.net

import io.cloudcauldron.bocan.sync.heldCertificate
import io.cloudcauldron.bocan.sync.heldIdentity
import io.cloudcauldron.bocan.sync.identity.Fingerprints
import io.cloudcauldron.bocan.sync.tlsMockWebServer
import java.io.IOException
import java.security.cert.X509Certificate
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class SyncHttpClientFactoryTests {
    private val phone = heldIdentity()
    private val macCert = heldCertificate("bocan-mac-11112222")
    private val macFingerprint = Fingerprints.ofCertificate(macCert.certificate)
    private val factory = SyncHttpClientFactory(phone)

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = tlsMockWebServer(macCert, phone.certificate)
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `paired client reaches ping and presents the device certificate`() {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        val response = factory.pairedClient(macFingerprint)
            .newCall(Request.Builder().url(server.url("/v1/ping")).build())
            .execute()
        response.use { assertEquals(200, it.code) }

        val recorded = server.takeRequest()
        val presented = requireNotNull(recorded.handshake).peerCertificates.first() as X509Certificate
        assertEquals(phone.fingerprint, Fingerprints.ofCertificate(presented))
    }

    @Test
    fun `pairing client captures the server certificate it saw on the handshake`() {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        val pairing = factory.pairingClient(macFingerprint)
        pairing.client.newCall(Request.Builder().url(server.url("/v1/ping")).build()).execute()
            .use { assertEquals(200, it.code) }
        assertEquals(macFingerprint, Fingerprints.ofCertificate(requireNotNull(pairing.serverCertificate)))
    }

    @Test
    fun `paired client refuses a server whose certificate does not match the pin`() {
        val client = factory.pairedClient("00".repeat(32))
        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url(server.url("/v1/ping")).build()).execute()
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `pairing client refuses a non-pairing endpoint before any request is sent`() {
        val client = factory.pairingClient(macFingerprint).client
        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url(server.url("/v1/manifest")).build()).execute()
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `pairing client permits ping and the pairing endpoints`() {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        val client = factory.pairingClient(macFingerprint).client

        client.newCall(Request.Builder().url(server.url("/v1/ping")).build()).execute()
            .use { assertEquals(200, it.code) }
        val body = "{}".toRequestBody("application/json".toMediaType())
        client.newCall(Request.Builder().url(server.url("/v1/pair/start")).post(body).build()).execute()
            .use { assertEquals(200, it.code) }
    }
}
