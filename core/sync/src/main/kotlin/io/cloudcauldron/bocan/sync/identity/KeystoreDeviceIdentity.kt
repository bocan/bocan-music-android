package io.cloudcauldron.bocan.sync.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.security.auth.x500.X500Principal

/**
 * The production [DeviceIdentity]: a P-256 signing key in the Android Keystore
 * (non-exportable, no user-auth requirement) plus the self-signed certificate
 * the Keystore mints for it. The KeyManager delegates signing to the Keystore,
 * so the private key never leaves secure hardware.
 *
 * Not unit-testable off-device: Robolectric does not implement the
 * "AndroidKeyStore" provider, so this class is exercised only by instrumented
 * tests and excluded from the coverage floor. The pure pieces it relies on
 * ([Fingerprints], [DeviceCommonName]) are covered directly.
 */
class KeystoreDeviceIdentity private constructor(override val certificate: X509Certificate, private val keyStore: KeyStore) :
    DeviceIdentity {
    override val fingerprint: String = Fingerprints.ofCertificate(certificate)

    override fun keyManagers(): Array<KeyManager> {
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore, null)
        return factory.keyManagers
    }

    companion object {
        const val DEFAULT_ALIAS = "bocan-device-identity"
        private const val PROVIDER = "AndroidKeyStore"
        private const val CURVE = "secp256r1"
        private const val VALIDITY_YEARS = 25L
        private const val SERIAL_BITS = 128

        /** Load the existing identity, generating the key and certificate on first use. */
        fun loadOrCreate(alias: String = DEFAULT_ALIAS): KeystoreDeviceIdentity {
            val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(alias)) {
                generate(alias)
            }
            val certificate = keyStore.getCertificate(alias) as X509Certificate
            return KeystoreDeviceIdentity(certificate, keyStore)
        }

        private fun generate(alias: String) {
            val now = Instant.now()
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                // DIGEST_NONE is required alongside SHA-256: Conscrypt's TLS 1.3 client-auth
                // path pre-hashes the handshake transcript and asks the Keystore for a raw
                // (NONEwithECDSA) signature of that digest. A key that permits only
                // DIGEST_SHA256 refuses the raw operation, so the CertificateVerify signature
                // fails ("EcdsaMethodDoSign") and the mutual-TLS handshake never completes.
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                .setCertificateSubject(X500Principal("CN=${DeviceCommonName.random()}"))
                .setCertificateSerialNumber(BigInteger(SERIAL_BITS, SecureRandom()))
                .setCertificateNotBefore(Date.from(now))
                .setCertificateNotAfter(Date.from(now.plus(VALIDITY_YEARS * DAYS_PER_YEAR, ChronoUnit.DAYS)))
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).apply {
                initialize(spec)
                generateKeyPair()
            }
        }

        private const val DAYS_PER_YEAR = 365L
    }
}
