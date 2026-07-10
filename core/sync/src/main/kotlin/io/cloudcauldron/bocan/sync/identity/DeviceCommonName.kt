package io.cloudcauldron.bocan.sync.identity

import java.security.SecureRandom

/**
 * The device certificate common name, per sync-protocol.md section 2:
 * `bocan-android-<8 random hex>`. Pure so the format is testable without the
 * Keystore.
 */
object DeviceCommonName {
    const val PREFIX = "bocan-android-"
    private const val RANDOM_BYTES = 4
    private val FORMAT = Regex("bocan-android-[0-9a-f]{8}")

    /** A fresh common name with eight random lowercase hex characters. */
    fun random(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(RANDOM_BYTES)
        random.nextBytes(bytes)
        return PREFIX + Fingerprints.toHex(bytes)
    }

    /** True if [commonName] is exactly the protocol format. */
    fun isValid(commonName: String): Boolean = FORMAT.matches(commonName)
}
