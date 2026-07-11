package io.cloudcauldron.bocan.scrobble.providers

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class LastFmSignatureTests {
    @Test
    fun `known answer, filtered params empty leaves MD5 of the secret`() {
        // With every real param excluded, the signed string is just the secret.
        // MD5("secret") = 5ebe2294ecd0e0f08eab7690d2a6ee69 (canonical known answer).
        assertEquals("5ebe2294ecd0e0f08eab7690d2a6ee69", LastFmSignature.sign(mapOf("format" to "json"), secret = "secret"))
    }

    @Test
    fun `known answer for sorted concatenation`() {
        // Signed string = "api_keyKmethodauth.getToken" + "secret" -> MD5, verified independently.
        val sig = LastFmSignature.sign(mapOf("method" to "auth.getToken", "api_key" to "K"), secret = "secret")
        assertEquals(md5Hex("api_keyKmethodauth.getTokensecret"), sig)
    }

    @Test
    fun `format and callback are excluded from the signature`() {
        val base = mapOf("method" to "auth.getToken", "api_key" to "K")
        val withNoise = base + mapOf("format" to "json", "callback" to "cb")
        assertEquals(LastFmSignature.sign(base, "s"), LastFmSignature.sign(withNoise, "s"))
    }

    @Test
    fun `output is lower-case hex of length 32`() {
        val sig = LastFmSignature.sign(mapOf("a" to "1"), secret = "x")
        assertEquals(32, sig.length)
        assertEquals(sig.lowercase(), sig)
        assertTrue(sig.all { it in "0123456789abcdef" })
    }

    private fun md5Hex(input: String): String =
        java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}
