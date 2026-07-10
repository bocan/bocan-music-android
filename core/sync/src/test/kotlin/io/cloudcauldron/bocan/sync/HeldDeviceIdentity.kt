package io.cloudcauldron.bocan.sync

import io.cloudcauldron.bocan.sync.identity.DeviceIdentity
import io.cloudcauldron.bocan.sync.identity.Fingerprints
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/**
 * A real secp256r1 identity backed by an in-memory HeldCertificate. Stands in
 * for [io.cloudcauldron.bocan.sync.identity.KeystoreDeviceIdentity] in tests,
 * which cannot reach the AndroidKeyStore provider off-device.
 */
class HeldDeviceIdentity(val held: HeldCertificate) : DeviceIdentity {
    private val handshake = HandshakeCertificates.Builder().heldCertificate(held).build()
    override val certificate: X509Certificate = held.certificate
    override val fingerprint: String = Fingerprints.ofCertificate(held.certificate)
    override fun keyManagers(): Array<KeyManager> = arrayOf(handshake.keyManager)
}
