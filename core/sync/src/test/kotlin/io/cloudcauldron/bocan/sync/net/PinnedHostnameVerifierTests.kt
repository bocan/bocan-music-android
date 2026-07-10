package io.cloudcauldron.bocan.sync.net

import io.cloudcauldron.bocan.sync.heldCertificate
import io.cloudcauldron.bocan.sync.identity.Fingerprints
import java.lang.reflect.Proxy
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedHostnameVerifierTests {
    private val serverCert = heldCertificate("bocan-mac-abcd1234").certificate
    private val fingerprint = Fingerprints.ofCertificate(serverCert)

    @Test
    fun `verifies a session presenting the pinned certificate`() {
        assertTrue(PinnedHostnameVerifier(fingerprint).verify("any-host", sessionPresenting(serverCert)))
    }

    @Test
    fun `rejects a session presenting a different certificate`() {
        val other = heldCertificate("bocan-mac-0000ffff").certificate
        assertFalse(PinnedHostnameVerifier(fingerprint).verify("any-host", sessionPresenting(other)))
    }

    @Test
    fun `rejects a session with no peer certificate and a null session`() {
        assertFalse(PinnedHostnameVerifier(fingerprint).verify("any-host", sessionPresenting(null)))
        assertFalse(PinnedHostnameVerifier(fingerprint).verify("any-host", null))
    }

    /** A minimal SSLSession that answers only getPeerCertificates. */
    private fun sessionPresenting(cert: X509Certificate?): SSLSession =
        Proxy.newProxyInstance(SSLSession::class.java.classLoader, arrayOf(SSLSession::class.java)) { _, method, _ ->
            if (method.name == "getPeerCertificates") {
                if (cert != null) arrayOf<Certificate>(cert) else emptyArray<Certificate>()
            } else {
                null
            }
        } as SSLSession
}
