package io.cloudcauldron.bocan.sync.net

import io.cloudcauldron.bocan.sync.identity.Fingerprints
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

/**
 * The protocol does no hostname verification: the pinned certificate is the
 * whole trust decision (sync-protocol.md section 3). Rather than accept every
 * hostname (which, like an accept-any trust manager, a security scanner flags),
 * this verifier re-checks the pinned fingerprint of the peer certificate. It
 * returns false on any mismatch, so it never trusts blindly; in practice the
 * pinned trust manager has already rejected a wrong certificate before this runs.
 */
internal class PinnedHostnameVerifier(private val expectedFingerprint: String) : HostnameVerifier {
    override fun verify(hostname: String?, session: SSLSession?): Boolean {
        val leaf = session?.peerCertificates?.firstOrNull() as? X509Certificate ?: return false
        return Fingerprints.matches(expectedFingerprint, Fingerprints.ofCertificate(leaf))
    }
}
