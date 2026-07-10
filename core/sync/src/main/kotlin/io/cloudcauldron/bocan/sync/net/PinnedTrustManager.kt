package io.cloudcauldron.bocan.sync.net

import io.cloudcauldron.bocan.sync.identity.Fingerprints
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Trusts exactly one server certificate: the one whose DER SHA-256 equals
 * [expectedFingerprint]. This is the entire trust decision (sync-protocol.md
 * section 3): no CA chains, no system trust store, no hostname check here.
 *
 * [checkServerTrusted] throws [CertificateException] on any mismatch and is
 * never empty, so the app contains no accept-any trust manager (an accept-any
 * checkServerTrusted is a Google Play security-scan rejection, not a warning).
 *
 * It also records the presented leaf certificate: the pairing ceremony reads it
 * back from here rather than from OkHttp's response.handshake, whose
 * peerCertificates the JVM's JSSE leaves empty for a custom trust manager.
 */
internal class PinnedTrustManager(private val expectedFingerprint: String) : X509TrustManager {
    @Volatile
    var lastServerCertificate: X509Certificate? = null
        private set

    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("empty server certificate chain")
        val actual = Fingerprints.ofCertificate(leaf)
        if (!Fingerprints.matches(expectedFingerprint, actual)) {
            throw CertificateException("server certificate pin mismatch: expected $expectedFingerprint, got $actual")
        }
        lastServerCertificate = leaf
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
        // The phone never validates a client certificate; the Mac does that.
        throw CertificateException("client trust is not evaluated on the phone")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
