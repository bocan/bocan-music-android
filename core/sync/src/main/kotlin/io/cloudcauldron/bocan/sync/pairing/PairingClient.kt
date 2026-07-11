package io.cloudcauldron.bocan.sync.pairing

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.discovery.DiscoveredMac
import io.cloudcauldron.bocan.sync.identity.DeviceIdentity
import io.cloudcauldron.bocan.sync.identity.Fingerprints
import io.cloudcauldron.bocan.sync.net.PairedServerStore
import io.cloudcauldron.bocan.sync.net.SyncHttpClientFactory
import io.cloudcauldron.bocan.sync.net.SyncHttpClientFactory.PairingHttpClient
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * The pairing ceremony state machine (sync-protocol.md section 4, client side).
 *
 *   [start] opens TXT-pinned TLS to the tapped Mac, POSTs pair/start, takes the
 *   Mac certificate from the TLS layer (never the JSON), derives the expected
 *   code, and surfaces [PairingState.AwaitingCode]. [submitCode] compares the
 *   typed code locally: a wrong code never sends pair/confirm; a match POSTs the
 *   proof, persists the pinned relationship, and surfaces [PairingState.Paired].
 *
 * The security boundary is the local code comparison, so no confirm request is
 * ever sent for a code the user did not enter correctly.
 */
class PairingClient(
    private val identity: DeviceIdentity,
    private val clientFactory: SyncHttpClientFactory,
    private val trustStore: PairedServerStore,
    private val deviceName: String,
    private val dispatchers: CoroutineDispatchers,
    private val randomBytes: (Int) -> ByteArray = { size -> ByteArray(size).also(SECURE_RANDOM::nextBytes) }
) {
    private val log = AppLog.forCategory(LogCategory.Pairing)
    private val stateFlow = MutableStateFlow<PairingState>(PairingState.Discovering)
    val state: StateFlow<PairingState> = stateFlow.asStateFlow()

    private var session: Session? = null
    private var wrongAttempts = 0

    private class Session(
        val mac: DiscoveredMac,
        val pairing: PairingHttpClient,
        val sessionId: String,
        val serverName: String,
        val serverCertificate: X509Certificate,
        val expectedCode: String
    )

    /** Ceremony step 2: connect to [mac] and fetch the inputs for the code. */
    suspend fun start(mac: DiscoveredMac) {
        discardSession()
        if (mac.protocolVersion > PROTOCOL_VERSION) {
            fail(SyncError.UnsupportedProtocol(mac.protocolVersion, PROTOCOL_VERSION))
            return
        }
        val pairing = clientFactory.pairingClient(mac.fingerprint)
        val noncePhone = randomBytes(NONCE_BYTES)
        val request = jsonPost(
            mac,
            PATH_START,
            SyncJson.encodeToString(
                PairStartRequest(PROTOCOL_VERSION, deviceName, base64(noncePhone))
            )
        )
        try {
            withContext(dispatchers.io) { pairing.client.newCall(request).execute() }.use { response ->
                if (!response.isSuccessful) {
                    fail(errorFrom(response))
                    return
                }
                onStarted(mac, pairing, noncePhone, response)
            }
        } catch (e: IOException) {
            fail(networkError(e, mac))
        } catch (e: SerializationException) {
            fail(SyncError.MalformedResponse(e.message ?: "unparseable pair/start response"))
        }
    }

    private fun onStarted(mac: DiscoveredMac, pairing: PairingHttpClient, noncePhone: ByteArray, response: Response) {
        // The certificate comes from the trust manager that saw the TLS handshake, never the JSON.
        val serverCertificate = pairing.serverCertificate
            ?: run {
                fail(SyncError.MalformedResponse("no server certificate captured from the TLS handshake"))
                return
            }
        val start = SyncJson.decodeFromString<PairStartResponse>(response.body.string())
        if (start.protocolVersion > PROTOCOL_VERSION) {
            fail(SyncError.UnsupportedProtocol(start.protocolVersion, PROTOCOL_VERSION))
            return
        }
        val fingerprintMac = Fingerprints.ofCertificate(serverCertificate)
        val expectedCode = PairingCode.derive(
            fpMac = fingerprintMac,
            fpPhone = identity.fingerprint,
            noncePhone = noncePhone,
            nonceMac = Base64.getDecoder().decode(start.nonceMac)
        )
        session = Session(mac, pairing, start.sessionId, start.serverName, serverCertificate, expectedCode)
        wrongAttempts = 0
        // The code is logged under a redacted key; it is a verification code, not a secret.
        log.info("pairing.awaitingCode", mapOf("server" to start.serverName, "code" to expectedCode))
        stateFlow.value = PairingState.AwaitingCode(mac, expectedCode)
    }

    /** The user typed the six-digit code shown on the Mac. */
    suspend fun submitCode(code: String) {
        val current = session ?: run {
            fail(SyncError.MalformedResponse("no pairing session in progress"))
            return
        }
        if (code != current.expectedCode) {
            wrongAttempts++
            log.warning("pairing.codeMismatch", mapOf("attempts" to wrongAttempts))
            if (wrongAttempts >= MAX_CODE_ATTEMPTS) {
                discardSession()
                stateFlow.value = PairingState.Failed(SyncError.TooManyAttempts)
            } else {
                stateFlow.value = PairingState.Failed(SyncError.CodeMismatch)
            }
            return
        }
        confirm(current, code)
    }

    private suspend fun confirm(current: Session, code: String) {
        stateFlow.value = PairingState.Confirming(current.mac)
        val proof = base64(PairingCode.confirmProof(code, current.sessionId))
        // sessionId is a plain UUID; proof is redacted by the sensitive-key filter.
        log.debug("pairing.confirm", mapOf("sessionId" to current.sessionId, "proof" to proof))
        val request = jsonPost(
            current.mac,
            PATH_CONFIRM,
            SyncJson.encodeToString(PairConfirmRequest(current.sessionId, proof))
        )
        // The Mac holds the confirm response until the user clicks Trust on its
        // screen, up to the 120 s session lifetime (sync-protocol.md section 4),
        // so this one call must wait that out instead of the transport default.
        val confirmClient = current.pairing.client.newBuilder()
            .readTimeout(CONFIRM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        try {
            withContext(dispatchers.io) { confirmClient.newCall(request).execute() }.use { response ->
                if (!response.isSuccessful) {
                    fail(errorFrom(response))
                    return
                }
                persist(current, response)
            }
        } catch (e: IOException) {
            fail(networkError(e, current.mac))
        } catch (e: SerializationException) {
            fail(SyncError.MalformedResponse(e.message ?: "unparseable pair/confirm response"))
        }
    }

    private suspend fun persist(current: Session, response: Response) {
        val confirmed = SyncJson.decodeFromString<PairConfirmResponse>(response.body.string())
        // The pinned fingerprint comes from the TLS certificate, never the JSON.
        trustStore.save(
            SyncServerEntity(
                serverId = confirmed.serverId,
                serverName = current.serverName,
                certFingerprint = Fingerprints.ofCertificate(current.serverCertificate),
                certDer = current.serverCertificate.encoded,
                lastAppliedGeneration = 0,
                lastSyncAt = null,
                pairedAt = Instant.now()
            )
        )
        log.info("pairing.paired", mapOf("server" to current.serverName))
        discardSession()
        stateFlow.value = PairingState.Paired(current.serverName)
    }

    /** Abandon the ceremony and return to discovery. */
    fun reset() {
        discardSession()
        stateFlow.value = PairingState.Discovering
    }

    private fun discardSession() {
        session = null
        wrongAttempts = 0
    }

    private fun fail(error: SyncError) {
        log.warning("pairing.failed", mapOf("error" to error.toString()))
        stateFlow.value = PairingState.Failed(error)
    }

    private fun errorFrom(response: Response): SyncError {
        val retryAfter = response.header("Retry-After")?.toIntOrNull()
        val bodyText = response.body.string()
        val parsed = parseError(bodyText)
        return if (parsed != null) {
            SyncError.fromServer(parsed.error, response.code, parsed.message, retryAfter)
        } else {
            SyncError.Server("http_${response.code}", response.code, bodyText.take(MAX_ERROR_SNIPPET))
        }
    }

    private fun parseError(bodyText: String): ErrorBody? = try {
        SyncJson.decodeFromString<ErrorBody>(bodyText)
    } catch (e: SerializationException) {
        log.debug("pairing.errorBodyUnparsed", mapOf("error" to e.toString()))
        null
    }

    private companion object {
        const val PROTOCOL_VERSION = 1
        const val NONCE_BYTES = 32
        const val MAX_CODE_ATTEMPTS = 3
        const val MAX_ERROR_SNIPPET = 200
        const val PATH_START = "/v1/pair/start"
        const val PATH_CONFIRM = "/v1/pair/confirm"

        // The 120 s pairing session lifetime plus margin for the network.
        const val CONFIRM_READ_TIMEOUT_SECONDS = 130L
        val SECURE_RANDOM = SecureRandom()
    }
}

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

private fun networkError(cause: Throwable, mac: DiscoveredMac): SyncError {
    val certificateProblem = generateSequence(cause) { it.cause }.any { it is CertificateException }
    val url = "https://${mac.host.hostAddress}:${mac.port}"
    return if (certificateProblem) {
        SyncError.CertificatePinMismatch(expectedFingerprint = mac.fingerprint, actualFingerprint = null)
    } else {
        SyncError.Network(url, cause)
    }
}

private fun jsonPost(mac: DiscoveredMac, path: String, json: String): Request = Request.Builder()
    .url(urlFor(mac, path))
    .post(json.toRequestBody(JSON_MEDIA_TYPE))
    .build()

private fun urlFor(mac: DiscoveredMac, path: String): HttpUrl {
    val host: String = mac.host.hostAddress
    return HttpUrl.Builder()
        .scheme("https")
        .host(host)
        .port(mac.port)
        .encodedPath(path)
        .build()
}

private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
