package io.cloudcauldron.bocan.sync.engine

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.model.manifest.Manifest
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestCodec
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.SyncError
import java.io.IOException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** `GET /v1/ping` liveness and generation (sync-protocol.md section 6). */
@Serializable
data class PingResponse(val protocolVersion: Int, val serverId: String, val generation: Long)

/**
 * Resolves the current network endpoint of the paired Mac. The host and port are
 * not persisted (identity is the pinned certificate, not the address); they come
 * from live mDNS discovery. Returns null when the Mac is not currently visible,
 * which the engine surfaces as [SyncState.ServerUnreachable].
 */
fun interface SyncEndpointProvider {
    suspend fun current(): HttpUrl?
}

/**
 * The paired-side HTTP surface: ping and manifest. File bytes go through
 * [Downloader]. One pinned [OkHttpClient] serves every endpoint because the trust
 * decision is the certificate pin, not the host, so a changing LAN address needs
 * no new client.
 */
class SyncApi(private val client: OkHttpClient, private val dispatchers: CoroutineDispatchers) {
    private val log = AppLog.forCategory(LogCategory.Network)

    /** `GET /v1/ping`. Throws [SyncError.Network] on transport failure. */
    suspend fun ping(base: HttpUrl): PingResponse = withContext(dispatchers.io) {
        val url = base.resolvePath(PATH_PING)
        execute(url) { body -> decode<PingResponse>(body) }
    }

    /** `GET /v1/manifest`. OkHttp transparently negotiates gzip and inflates it. */
    suspend fun manifest(base: HttpUrl): Manifest = withContext(dispatchers.io) {
        val url = base.resolvePath(PATH_MANIFEST)
        execute(url) { body -> ManifestCodec.decode(body) }
    }

    private inline fun <T> execute(url: HttpUrl, parse: (String) -> T): T {
        call(url).use { response ->
            if (!response.isSuccessful) throw errorFrom(response)
            val body = response.body.string()
            return try {
                parse(body)
            } catch (e: SerializationException) {
                throw SyncError.MalformedResponse(e.message ?: "unparseable response from $url", e)
            }
        }
    }

    private fun call(url: HttpUrl): Response = try {
        client.newCall(Request.Builder().url(url).get().build()).execute()
    } catch (e: IOException) {
        throw SyncError.Network(url.toString(), e)
    }

    private fun errorFrom(response: Response): SyncError {
        val retryAfter = response.header("Retry-After")?.toIntOrNull()
        val bodyText = response.body.string()
        val parsed = try {
            json.decodeFromString<ErrorEnvelope>(bodyText)
        } catch (e: SerializationException) {
            log.debug("sync.errorBodyUnparsed", mapOf("error" to e.toString()))
            null
        }
        return if (parsed != null) {
            SyncError.fromServer(parsed.error, response.code, parsed.message, retryAfter)
        } else {
            SyncError.Server("http_${response.code}", response.code, bodyText.take(MAX_SNIPPET))
        }
    }

    private inline fun <reified T> decode(body: String): T = json.decodeFromString(body)

    @Serializable
    private data class ErrorEnvelope(val error: String, val message: String? = null)

    private companion object {
        const val PATH_PING = "v1/ping"
        const val PATH_MANIFEST = "v1/manifest"
        const val MAX_SNIPPET = 200
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

/** Append [path] segments to a base URL, tolerating a trailing slash on the base. */
internal fun HttpUrl.resolvePath(path: String): HttpUrl = newBuilder().addPathSegments(path).build()
