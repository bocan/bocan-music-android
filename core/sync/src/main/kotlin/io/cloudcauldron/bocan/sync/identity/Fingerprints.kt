package io.cloudcauldron.bocan.sync.identity

import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * Certificate fingerprints, per sync-protocol.md section 2: the lowercase hex
 * SHA-256 of a certificate's DER encoding. Pure and platform-free so both the
 * trust layer and the pairing ceremony compute identical values.
 */
object Fingerprints {
    /** The protocol fingerprint of a certificate: lowercase hex SHA-256 of its DER bytes. */
    fun ofCertificate(certificate: X509Certificate): String = sha256Hex(certificate.encoded)

    /** Lowercase hex SHA-256 of arbitrary bytes. */
    fun sha256Hex(bytes: ByteArray): String = toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** Fingerprints are public, but compare them case-insensitively to be forgiving of hex casing. */
    fun matches(expected: String, actual: String): Boolean = expected.equals(actual, ignoreCase = true)

    /** Lowercase hex encoding without java.util.HexFormat, which is not on minSdk 29. */
    internal fun toHex(bytes: ByteArray): String = buildString(bytes.size * HEX_PER_BYTE) {
        bytes.forEach { byte ->
            val value = byte.toInt() and BYTE_MASK
            append(HEX[value ushr NIBBLE_BITS]).append(HEX[value and NIBBLE_MASK])
        }
    }

    private const val HEX = "0123456789abcdef"
    private const val HEX_PER_BYTE = 2
    private const val BYTE_MASK = 0xFF
    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0xF
}
