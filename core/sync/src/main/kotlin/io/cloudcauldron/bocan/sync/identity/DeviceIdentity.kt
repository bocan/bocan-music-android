package io.cloudcauldron.bocan.sync.identity

import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager

/**
 * This device's stable TLS identity: a P-256 key pair and a self-signed
 * certificate, generated once and never rotated (repairing is the recovery
 * story). The private key is non-exportable; only the certificate, its
 * fingerprint, and KeyManagers that delegate signing to the key are exposed.
 *
 * The production implementation ([KeystoreDeviceIdentity]) keeps the key in the
 * Android Keystore. Tests supply an in-memory identity so the ceremony and trust
 * logic run off-device without the Keystore provider.
 */
interface DeviceIdentity {
    /** The self-signed certificate presented for mutual TLS. */
    val certificate: X509Certificate

    /** Lowercase hex SHA-256 of [certificate]'s DER encoding (this device's fpPhone). */
    val fingerprint: String

    /** KeyManagers that present [certificate] and sign the TLS handshake with the private key. */
    fun keyManagers(): Array<KeyManager>
}
