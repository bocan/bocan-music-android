package io.cloudcauldron.bocan.sync.identity

import io.cloudcauldron.bocan.sync.heldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintsTests {
    @Test
    fun `sha256Hex matches known vectors`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Fingerprints.sha256Hex(ByteArray(0)))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Fingerprints.sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun `sha256Hex is always 64 lowercase hex chars`() {
        val hex = Fingerprints.sha256Hex(byteArrayOf(-1, 0, 127, -128))
        assertEquals(64, hex.length)
        assertTrue(hex.all { it in "0123456789abcdef" })
    }

    @Test
    fun `ofCertificate hashes the DER encoding`() {
        val cert = heldCertificate().certificate
        assertEquals(Fingerprints.sha256Hex(cert.encoded), Fingerprints.ofCertificate(cert))
    }

    @Test
    fun `matches ignores hex casing but rejects different fingerprints`() {
        val fp = Fingerprints.ofCertificate(heldCertificate().certificate)
        assertTrue(Fingerprints.matches(fp, fp.uppercase()))
        assertFalse(Fingerprints.matches(fp, "00".repeat(32)))
    }
}
