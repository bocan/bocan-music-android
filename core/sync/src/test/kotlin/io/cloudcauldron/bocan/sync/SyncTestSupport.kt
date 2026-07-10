package io.cloudcauldron.bocan.sync

import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import java.security.cert.X509Certificate
import java.time.Instant
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/** A self-signed EC leaf certificate with the given common name. */
fun heldCertificate(commonName: String = "bocan-android-deadbeef"): HeldCertificate =
    HeldCertificate.Builder().commonName(commonName).ecdsa256().build()

fun heldIdentity(commonName: String = "bocan-android-deadbeef"): HeldDeviceIdentity = HeldDeviceIdentity(heldCertificate(commonName))

/**
 * A TLS MockWebServer that presents [serverCert] and requires a client
 * certificate it trusts. The caller starts and closes it. Mutual TLS mirrors the
 * real Mac: an untrusted or absent client certificate fails the handshake.
 */
fun tlsMockWebServer(serverCert: HeldCertificate, trustedClient: X509Certificate): MockWebServer {
    val serverHandshake = HandshakeCertificates.Builder()
        .heldCertificate(serverCert)
        .addTrustedCertificate(trustedClient)
        .build()
    return MockWebServer().apply {
        useHttps(serverHandshake.sslSocketFactory())
        requireClientAuth()
    }
}

/** A paired-server row for persistence tests. */
fun syncServerEntity(
    serverId: String = "server-1",
    serverName: String = "Test Mac",
    fingerprint: String = "ab".repeat(32),
    certDer: ByteArray = byteArrayOf(1, 2, 3),
    lastAppliedGeneration: Long = 0
): SyncServerEntity = SyncServerEntity(
    serverId = serverId,
    serverName = serverName,
    certFingerprint = fingerprint,
    certDer = certDer,
    lastAppliedGeneration = lastAppliedGeneration,
    lastSyncAt = null,
    pairedAt = Instant.EPOCH
)
