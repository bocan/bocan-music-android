package io.cloudcauldron.bocan.app.sync

import android.content.res.AssetManager
import io.cloudcauldron.bocan.app.fixtures.DiscoveryOverride
import io.cloudcauldron.bocan.persistence.model.manifest.Manifest
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestCodec
import io.cloudcauldron.bocan.sync.discovery.ResolvedService
import java.io.FileNotFoundException
import java.math.BigInteger
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Collections
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer

/**
 * A Mac for the end-to-end suite (phase 17 slice 6): the server half of sync-protocol.md v1 on
 * the loopback interface, inside the test process, serving the debug build's fixture library.
 * It never touches any other interface or the internet.
 *
 * It is written from the protocol document, not from the app's client code, so the app is
 * checked against the contract rather than against itself:
 * - its own P-256 certificate, advertised with `v`, `fp`, and `pm` TXT keys (section 1);
 * - mutual TLS: any client certificate while in pairing mode, afterwards only the paired
 *   phone's (section 3); the phone's fingerprint always comes from the TLS layer;
 * - the pairing ceremony with its own code derivation and proof check (section 4), the code
 *   readable as [shownCode], the way a person reads it off the Mac's screen;
 * - every endpoint in section 6, with `ETag`, `If-Match`, open `Range`, and the error envelope.
 *
 * The person's "Trust" click (section 4, step 5) is implied: a valid proof is trusted at once.
 */
class FakeMac(private val assets: AssetManager) : AutoCloseable {
    private val identity: HeldCertificate = HeldCertificate.Builder()
        .commonName("bocan-mac-" + hex(randomBytes(4)))
        .ecdsa256()
        .build()

    /** The Mac's certificate fingerprint, the `fp` TXT value and what the phone pins. */
    val fingerprint: String = sha256Hex(identity.certificate.encoded)

    private val manifestBytes: ByteArray = asset(MANIFEST)
    val manifest: Manifest = ManifestCodec.decode(manifestBytes.decodeToString())

    @Volatile var pairingMode: Boolean = true

    /** The six digits the Mac shows for the current pairing session, null before pair/start. */
    @Volatile var shownCode: String? = null
        private set

    /** Fingerprints of the phones that completed pairing; the only client certificates accepted outside pairing mode. */
    val trustedPhones: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** Every request path, in order, for checks such as "no confirm was ever sent". */
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val sessions = Collections.synchronizedMap(mutableMapOf<String, Session>())

    @Volatile private var slow = false
    private val server = MockWebServer()

    private class Session(val fpPhone: String, val code: String)

    fun start(): FakeMac {
        val keyManager = HandshakeCertificates.Builder().heldCertificate(identity).build().keyManager
        val tls = SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), arrayOf(ClientTrust()), null) }
        server.useHttps(tls.socketFactory)
        server.requestClientAuth()
        server.dispatcher = Routes()
        server.start(InetAddress.getLoopbackAddress(), 0)
        running = this
        return this
    }

    /** A six-digit code that is not [shownCode], as a person mistyping it would enter. */
    fun wrongCode(): String {
        val shown = checkNotNull(shownCode) { "the Mac shows no code: pair/start never ran" }
        return ((shown.toInt() + 1) % CODE_MODULUS.toInt()).toString().padStart(CODE_DIGITS, '0')
    }

    /** The mDNS advert this Mac would publish, for the debug build's discovery override. */
    fun advert(): ResolvedService = ResolvedService(
        serviceName = SERVICE_NAME,
        host = InetAddress.getLoopbackAddress(),
        port = server.port,
        txt = mapOf("v" to "1", "fp" to fingerprint, "pm" to if (pairingMode) "1" else "0")
    )

    /** Put this Mac's current advert where the debug build's discovery looks, as Bonjour would. */
    fun publish() = DiscoveryOverride.advertise(listOf(advert()))

    /**
     * Trickle every file from now on, a few bytes a second, so a test can act while a sync is
     * mid-transfer. Like a slow Mac, not a dead one: bytes keep arriving, so the phone's read
     * timeout never fires and the transfer stays live until [normalFiles] or a cancel.
     */
    fun slowFiles() {
        slow = true
    }

    fun normalFiles() {
        slow = false
    }

    override fun close() {
        running = null
        DiscoveryOverride.clear()
        server.close()
    }

    /** Section 3: any client certificate in pairing mode (recorded through the handshake), else only a paired one. */
    private inner class ClientTrust : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
            val phone = chain.firstOrNull() ?: throw CertificateException("no client certificate")
            if (!pairingMode && sha256Hex(phone.encoded) !in trustedPhones) throw CertificateException("unpaired phone")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) =
            throw CertificateException("the fake Mac is never a TLS client")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private inner class Routes : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.encodedPath
            requests += path
            val phone = request.handshake?.peerCertificates?.firstOrNull() as? X509Certificate
                ?: return error(FORBIDDEN, "notPaired", "No client certificate")
            val fpPhone = sha256Hex(phone.encoded)
            val segments = path.removePrefix("/v1/").split('/')
            return when {
                path == "/v1/ping" -> json(ping())
                path == "/v1/pair/start" -> pairStart(fpPhone, request.body?.utf8().orEmpty())
                path == "/v1/pair/confirm" -> pairConfirm(fpPhone, request.body?.utf8().orEmpty())
                fpPhone !in trustedPhones -> error(FORBIDDEN, "notPaired", "This phone is not paired")
                path == "/v1/manifest" -> MockResponse.Builder().addHeader("Content-Type", JSON).body(Buffer().write(manifestBytes)).build()
                segments.take(2) == listOf("file", "track") -> trackFile(segments[2].toLongOrNull(), request)
                segments.take(2) == listOf("file", "episode") -> episodeFile(segments[2], request)
                segments.first() == "artwork" -> artwork(segments[1], request)
                segments.first() == "lyrics" -> lyrics(segments[1].toLongOrNull())
                segments.first() == "chapters" -> chapters(segments[1])
                else -> notFound()
            }
        }
    }

    private fun ping(): String = buildJsonObject {
        put("protocolVersion", PROTOCOL_VERSION)
        put("serverId", manifest.serverId)
        put("generation", manifest.generation)
    }.toString()

    private fun pairStart(fpPhone: String, body: String): MockResponse {
        if (!pairingMode) return error(FORBIDDEN, "pairingExpired", "Pairing mode is off")
        val noncePhone = Base64.getDecoder().decode(Json.parseToJsonElement(body).jsonObject.getValue("noncePhone").jsonPrimitive.content)
        val nonceMac = randomBytes(NONCE_BYTES)
        val sessionId = UUID.randomUUID().toString()
        val code = pairingCode(fingerprint, fpPhone, noncePhone, nonceMac)
        sessions[sessionId] = Session(fpPhone, code)
        shownCode = code
        return json(
            buildJsonObject {
                put("protocolVersion", PROTOCOL_VERSION)
                put("serverName", SERVICE_NAME)
                put("nonceMac", Base64.getEncoder().encodeToString(nonceMac))
                put("sessionId", sessionId)
            }.toString()
        )
    }

    private fun pairConfirm(fpPhone: String, body: String): MockResponse {
        val fields = Json.parseToJsonElement(body).jsonObject
        val sessionId = fields.getValue("sessionId").jsonPrimitive.content
        val session = sessions[sessionId]?.takeIf { it.fpPhone == fpPhone } ?: return error(FORBIDDEN, "pairingExpired", "No such session")
        val expected = Base64.getEncoder().encodeToString(confirmProof(session.code, sessionId))
        if (fields.getValue("proof").jsonPrimitive.content != expected) return error(FORBIDDEN, "badProof", "The proof does not match")
        trustedPhones += fpPhone
        sessions.clear()
        // Pairing mode ends (section 4, step 6) and the advert says so.
        pairingMode = false
        publish()
        return json(
            buildJsonObject {
                put("status", "paired")
                put("serverId", manifest.serverId)
            }.toString()
        )
    }

    private fun trackFile(id: Long?, request: RecordedRequest): MockResponse {
        val track = manifest.tracks.firstOrNull { it.id == id } ?: return notFound()
        return file("$LIBRARY/${track.relPath}", track.sha256, request)
    }

    private fun episodeFile(id: String, request: RecordedRequest): MockResponse {
        val episode = manifest.episodes.firstOrNull { it.id == id } ?: return notFound()
        return file("$ROOT/${episode.relPath}", episode.sha256, request)
    }

    private fun artwork(hash: String, request: RecordedRequest): MockResponse = file("$ROOT/artwork/$hash", hash, request)

    /** Section 6 file rules: `ETag` is the hash, a stale `If-Match` is 412, an open `Range` is 206 or 416. */
    private fun file(assetPath: String, sha256: String, request: RecordedRequest): MockResponse {
        val bytes = try {
            asset(assetPath)
        } catch (missing: FileNotFoundException) {
            return notFound()
        }
        request.headers["If-Match"]?.let { if (it != sha256) return error(PRECONDITION_FAILED, "notFound", "The file changed") }
        val start = request.headers["Range"]?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull() ?: 0
        if (start >= bytes.size && start > 0) return error(RANGE_NOT_SATISFIABLE, "notFound", "Range past the end")
        return MockResponse.Builder()
            .code(if (start > 0) PARTIAL else OK)
            .addHeader("ETag", sha256)
            .addHeader("Content-Type", "application/octet-stream")
            .apply { if (start > 0) addHeader("Content-Range", "bytes $start-${bytes.size - 1}/${bytes.size}") }
            .apply { if (slow) throttleBody(TRICKLE_BYTES, 1, TimeUnit.SECONDS) }
            .body(Buffer().write(bytes, start, bytes.size - start))
            .build()
    }

    private fun lyrics(trackId: Long?): MockResponse {
        val track = manifest.tracks.firstOrNull { it.id == trackId && it.lyricsHash != null } ?: return notFound()
        val kinds = Json.parseToJsonElement(asset("$ROOT/lyrics/kinds.json").decodeToString()).jsonObject
        val kind = kinds.getValue(track.id.toString()).jsonPrimitive.content
        val text = asset("$ROOT/lyrics/${track.id}.${if (kind == "synced") "lrc" else "txt"}").decodeToString()
        return json(
            buildJsonObject {
                put("trackId", track.id)
                put("kind", kind)
                put("text", text)
            }.toString()
        )
    }

    private fun chapters(episodeId: String): MockResponse = try {
        json(asset("$ROOT/chapters/$episodeId.json").decodeToString())
    } catch (missing: FileNotFoundException) {
        notFound()
    }

    private fun asset(path: String): ByteArray = assets.open(path).use { it.readBytes() }

    private fun json(body: String): MockResponse = MockResponse.Builder().addHeader("Content-Type", JSON).body(body).build()

    private fun notFound(): MockResponse = error(NOT_FOUND, "notFound", "Not found")

    private fun error(status: Int, code: String, message: String): MockResponse = MockResponse.Builder()
        .code(status)
        .addHeader("Content-Type", JSON)
        .body(
            buildJsonObject {
                put("error", code)
                put("message", message)
            }.toString()
        )
        .build()

    companion object {
        const val SERVICE_NAME = "E2E Mac"

        /** The Mac the current test started, for crawl tables, which are built before it exists. */
        @Volatile var running: FakeMac? = null
            private set

        fun current(): FakeMac = checkNotNull(running) { "no fake Mac is running: add FakeMacRule to the test" }

        private const val PROTOCOL_VERSION = 1
        private const val NONCE_BYTES = 32
        private const val CODE_DIGITS = 6
        private const val CODE_BYTES = 8
        private val CODE_MODULUS = BigInteger.valueOf(1_000_000)
        private const val LABEL = "bocan-pair-v1"
        private const val ROOT = "fixture"
        private const val LIBRARY = "fixture/library"
        private const val MANIFEST = "fixture/manifest.json"
        private const val JSON = "application/json"
        private const val OK = 200
        private const val PARTIAL = 206
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404
        private const val PRECONDITION_FAILED = 412
        private const val RANGE_NOT_SATISFIABLE = 416

        // The smallest fixture cover is about 1.8 KB, so a slow transfer lasts over ten seconds.
        private const val TRICKLE_BYTES = 128L
        private val random = SecureRandom()

        /** Section 4, step 3, from the document: HMAC-SHA256 keyed by both nonces over the label and both fingerprints. */
        fun pairingCode(fpMac: String, fpPhone: String, noncePhone: ByteArray, nonceMac: ByteArray): String {
            val (low, high) = listOf(fpMac, fpPhone).sorted()
            val digest = hmac(noncePhone + nonceMac, (LABEL + low + high).toByteArray(Charsets.US_ASCII))
            return BigInteger(1, digest.copyOf(CODE_BYTES)).mod(CODE_MODULUS).toString().padStart(CODE_DIGITS, '0')
        }

        /** Section 4, step 5: HMAC-SHA256 keyed by the code's ASCII over the session id's ASCII. */
        fun confirmProof(code: String, sessionId: String): ByteArray =
            hmac(code.toByteArray(Charsets.US_ASCII), sessionId.toByteArray(Charsets.US_ASCII))

        private fun hmac(key: ByteArray, message: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(message)

        private fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        private fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)
    }
}
