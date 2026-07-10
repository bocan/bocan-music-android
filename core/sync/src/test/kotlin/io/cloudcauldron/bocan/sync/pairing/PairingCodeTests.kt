package io.cloudcauldron.bocan.sync.pairing

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Golden-vector conformance for PairingCode. The vectors file is shared byte for
 * byte with the Mac repo (see scripts/gen-pairing-vectors.py); both
 * implementations must pass the identical fixture.
 */
class PairingCodeTests {
    @Serializable
    private data class VectorFile(val vectors: List<Vector>)

    @Serializable
    private data class Vector(
        val fpMac: String,
        val fpPhone: String,
        val noncePhoneBase64: String,
        val nonceMacBase64: String,
        val expectedCode: String,
        val sessionId: String,
        val expectedProofBase64: String
    )

    private val vectors: List<Vector> = loadVectors()

    @Test
    fun `derive matches every golden code`() {
        vectors.forEach { v ->
            val code = PairingCode.derive(v.fpMac, v.fpPhone, decode(v.noncePhoneBase64), decode(v.nonceMacBase64))
            assertEquals("code for session ${v.sessionId}", v.expectedCode, code)
        }
    }

    @Test
    fun `confirmProof matches every golden proof`() {
        vectors.forEach { v ->
            val proof = Base64.getEncoder().encodeToString(PairingCode.confirmProof(v.expectedCode, v.sessionId))
            assertEquals("proof for session ${v.sessionId}", v.expectedProofBase64, proof)
        }
    }

    @Test
    fun `every code is exactly six digits`() {
        vectors.forEach { v ->
            val code = PairingCode.derive(v.fpMac, v.fpPhone, decode(v.noncePhoneBase64), decode(v.nonceMacBase64))
            assertEquals(6, code.length)
            assert(code.all { it.isDigit() }) { "code was not all digits: $code" }
        }
    }

    @Test
    fun `swapping the two fingerprints yields the same code`() {
        vectors.forEach { v ->
            val phone = decode(v.noncePhoneBase64)
            val mac = decode(v.nonceMacBase64)
            val forward = PairingCode.derive(v.fpMac, v.fpPhone, phone, mac)
            val swapped = PairingCode.derive(v.fpPhone, v.fpMac, phone, mac)
            assertEquals("min/max normalisation should ignore fingerprint order", forward, swapped)
        }
    }

    @Test
    fun `different nonces yield different codes`() {
        val v = vectors.first()
        val base = PairingCode.derive(v.fpMac, v.fpPhone, decode(v.noncePhoneBase64), decode(v.nonceMacBase64))
        val altered = PairingCode.derive(v.fpMac, v.fpPhone, ByteArray(32) { 0x5A }, decode(v.nonceMacBase64))
        assertNotEquals(base, altered)
    }

    private fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)

    private fun loadVectors(): List<Vector> {
        val text = checkNotNull(javaClass.classLoader?.getResource("fixtures/pairing-vectors.json")) {
            "Missing pairing-vectors.json fixture"
        }.readText()
        return JSON.decodeFromString<VectorFile>(text).vectors
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
