package io.cloudcauldron.bocan.sync.pairing

import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The pairing code and confirm proof of sync-protocol.md section 4. Pure and
 * fully unit-testable: the Mac side derives the identical values from the same
 * golden vectors, so this must match byte for byte.
 *
 * The six-digit code is derived from both certificate fingerprints, so it is a
 * verification code and not a secret. A man-in-the-middle that terminates TLS
 * makes the two sides hold different fingerprint pairs, so they compute
 * different codes and the ceremony fails on comparison.
 */
object PairingCode {
    private const val LABEL = "bocan-pair-v1"
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val CODE_BYTES = 8
    private const val CODE_DIGITS = 6
    private val MODULUS: BigInteger = BigInteger.TEN.pow(CODE_DIGITS)

    /**
     * The verification code both devices display. Fingerprints are the lowercase
     * hex SHA-256 of each certificate's DER encoding; nonces are the raw 32-byte
     * values exchanged in pair/start, phone nonce first in the HMAC key.
     */
    fun derive(fpMac: String, fpPhone: String, noncePhone: ByteArray, nonceMac: ByteArray): String {
        val fpLo = minOf(fpMac, fpPhone)
        val fpHi = maxOf(fpMac, fpPhone)
        val key = noncePhone + nonceMac
        val msg = (LABEL + fpLo + fpHi).toByteArray(Charsets.US_ASCII)
        val digest = hmac(key, msg)
        val value = BigInteger(1, digest.copyOfRange(0, CODE_BYTES)).mod(MODULUS)
        return value.toString().padStart(CODE_DIGITS, '0')
    }

    /** The confirm proof of ceremony step 5: HMAC-SHA256(key = code ASCII, msg = sessionId ASCII). */
    fun confirmProof(code: String, sessionId: String): ByteArray =
        hmac(code.toByteArray(Charsets.US_ASCII), sessionId.toByteArray(Charsets.US_ASCII))

    private fun hmac(key: ByteArray, msg: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(msg)
    }
}
