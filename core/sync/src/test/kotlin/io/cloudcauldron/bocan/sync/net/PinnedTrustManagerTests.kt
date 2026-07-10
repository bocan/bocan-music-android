package io.cloudcauldron.bocan.sync.net

import io.cloudcauldron.bocan.sync.heldCertificate
import io.cloudcauldron.bocan.sync.identity.Fingerprints
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PinnedTrustManagerTests {
    private val serverCert = heldCertificate("bocan-mac-11112222").certificate
    private val fingerprint = Fingerprints.ofCertificate(serverCert)

    @Test
    fun `accepts the pinned certificate`() {
        PinnedTrustManager(fingerprint).checkServerTrusted(arrayOf(serverCert), "ECDHE_ECDSA")
    }

    @Test
    fun `rejects a different certificate`() {
        val other = heldCertificate("bocan-mac-99998888").certificate
        assertThrows(CertificateException::class.java) {
            PinnedTrustManager(fingerprint).checkServerTrusted(arrayOf(other), "ECDHE_ECDSA")
        }
    }

    @Test
    fun `rejects an empty or null chain`() {
        assertThrows(CertificateException::class.java) {
            PinnedTrustManager(fingerprint).checkServerTrusted(emptyArray(), "ECDHE_ECDSA")
        }
        assertThrows(CertificateException::class.java) {
            PinnedTrustManager(fingerprint).checkServerTrusted(null, "ECDHE_ECDSA")
        }
    }

    @Test
    fun `never validates a client certificate on the phone`() {
        assertThrows(CertificateException::class.java) {
            PinnedTrustManager(fingerprint).checkClientTrusted(arrayOf<X509Certificate>(serverCert), "ECDHE_ECDSA")
        }
    }

    @Test
    fun `accepted issuers is empty`() {
        assertEquals(0, PinnedTrustManager(fingerprint).acceptedIssuers.size)
    }
}
