package io.cloudcauldron.bocan.sync.net

import io.cloudcauldron.bocan.sync.identity.DeviceIdentity
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.TlsVersion

/**
 * Builds the two OkHttpClients the sync module ever uses. Both present this
 * device's client certificate for mutual TLS and pin the server certificate by
 * DER SHA-256; neither ever falls back to the system trust store.
 *
 *  - [pairingClient] pins the fingerprint advertised in the discovered TXT
 *    record and refuses every path but the pairing and ping endpoints. The TXT
 *    pin is defence in depth: the pairing code is the real MITM check.
 *  - [pairedClient] pins the stored server fingerprint for all later traffic.
 */
class SyncHttpClientFactory(private val identity: DeviceIdentity) {
    /** Pre-pairing client (sync-protocol.md sections 3 and 6). */
    fun pairingClient(expectedFingerprint: String): PairingHttpClient {
        val trustManager = PinnedTrustManager(expectedFingerprint)
        val client = baseBuilder(expectedFingerprint, trustManager)
            .addInterceptor(PairingPathInterceptor)
            .build()
        return PairingHttpClient(client, trustManager)
    }

    /** Post-pairing client pinned to the stored Mac certificate. */
    fun pairedClient(serverFingerprint: String): OkHttpClient =
        baseBuilder(serverFingerprint, PinnedTrustManager(serverFingerprint)).build()

    private fun baseBuilder(pinnedFingerprint: String, trustManager: PinnedTrustManager): OkHttpClient.Builder {
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(identity.keyManagers(), arrayOf(trustManager), SecureRandom())
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(PinnedHostnameVerifier(pinnedFingerprint))
            .connectionSpecs(listOf(PINNED_TLS))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /** Refuses any path a pre-pairing client must not touch, before the request is sent. */
    private object PairingPathInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val path = chain.request().url.encodedPath
            if (path != PING_PATH && !path.startsWith(PAIR_PATH_PREFIX)) {
                throw IOException("path $path is not permitted before pairing")
            }
            return chain.proceed(chain.request())
        }
    }

    /**
     * A pre-pairing client plus the server certificate its trust manager captured
     * during the TLS handshake. The ceremony takes the fingerprint from
     * [serverCertificate], never from the JSON.
     */
    class PairingHttpClient internal constructor(val client: OkHttpClient, private val trustManager: PinnedTrustManager) {
        val serverCertificate: X509Certificate?
            get() = trustManager.lastServerCertificate
    }

    private companion object {
        const val PING_PATH = "/v1/ping"
        const val PAIR_PATH_PREFIX = "/v1/pair/"
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val WRITE_TIMEOUT_SECONDS = 10L

        // Long enough for pairing and ping; phase 03 overrides per-call for file streams.
        const val READ_TIMEOUT_SECONDS = 30L

        // TLS 1.3 preferred, 1.2 permitted as a fallback per sync-protocol.md section 3.
        val PINNED_TLS: ConnectionSpec = ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
            .build()
    }
}
