package io.cloudcauldron.bocan.scrobble.providers

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.scrobble.AuthState
import io.cloudcauldron.bocan.scrobble.PlayEvent
import io.cloudcauldron.bocan.scrobble.ScrobbleError
import io.cloudcauldron.bocan.scrobble.SubmissionOutcome
import io.cloudcauldron.bocan.scrobble.SubmissionResult
import io.cloudcauldron.bocan.scrobble.auth.TokenKeys
import io.cloudcauldron.bocan.scrobble.auth.TokenStore
import io.cloudcauldron.bocan.scrobble.net.HttpResponse
import io.cloudcauldron.bocan.scrobble.net.ScrobbleHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Per-build Last.fm constants. The API key and shared secret are not cryptographic
 * secrets (every desktop client ships them), but they come from `local.properties` via
 * `BuildConfig` rather than source so they are easy to rotate and stay out of the repo. A
 * missing key leaves the provider [isConfigured] = false, which hides its settings UI
 * rather than crashing.
 */
data class LastFmConfig(val apiKey: String, val sharedSecret: String, val endpoint: HttpUrl = DEFAULT_ENDPOINT.toHttpUrl()) {
    val isConfigured: Boolean get() = apiKey.isNotEmpty() && sharedSecret.isNotEmpty()

    companion object {
        const val DEFAULT_ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
    }
}

/**
 * Last.fm (web service 2.0). Authenticated methods are signed form POSTs: params are MD5
 * signed by [LastFmSignature] before `format` and `api_sig` are appended. `track.scrobble`
 * batches up to 50 plays with indexed params. The web auth flow (getToken then getSession)
 * lands the session key here; the provider stores only that key and the username.
 */
// The provider surface (now-playing, scrobble, auth token, session, disconnect, signing)
// is the phase contract; its breadth is intentional, not a decomposition smell.
@Suppress("TooManyFunctions")
class LastFmProvider(
    private val config: LastFmConfig,
    private val tokens: TokenStore,
    private val http: ScrobbleHttp,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ScrobbleProvider {
    override val id = ProviderId.LAST_FM
    override val displayName = "Last.fm"
    private val log = AppLog.forCategory(LogCategory.Scrobble)

    override val authState: Flow<AuthState> =
        combine(tokens.observe(TokenKeys.LAST_FM_SESSION), tokens.observe(TokenKeys.LAST_FM_USERNAME)) { key, user ->
            if (key.isNullOrEmpty()) AuthState.Disconnected else AuthState.Connected(user)
        }

    override suspend fun isAuthenticated(): Boolean = !tokens.get(TokenKeys.LAST_FM_SESSION).isNullOrEmpty()

    override suspend fun updateNowPlaying(play: PlayEvent) {
        val session = requireSession()
        val params = buildMap {
            put("method", "track.updateNowPlaying")
            put("api_key", config.apiKey)
            put("sk", session)
            put("artist", play.artist)
            put("track", play.title)
            put("duration", play.durationSec.toString())
            play.album?.let { put("album", it) }
            play.albumArtist?.let { put("albumArtist", it) }
        }
        parseOrThrow(signedPost(params))
        log.debug("scrobble.lastfm.nowplaying.ok", mapOf("title" to play.title))
    }

    @Suppress("ReturnCount") // guard clauses for empty batch and missing session, then the submit path
    override suspend fun scrobble(batch: List<PlayEvent>): List<SubmissionResult> {
        if (batch.isEmpty()) return emptyList()
        val session = tokens.get(TokenKeys.LAST_FM_SESSION)
        if (session.isNullOrEmpty()) return batch.map { SubmissionResult(it.queueId, SubmissionOutcome.AuthExpired) }
        val params = buildMap {
            put("method", "track.scrobble")
            put("api_key", config.apiKey)
            put("sk", session)
            batch.forEachIndexed { index, play ->
                put("artist[$index]", play.artist)
                put("track[$index]", play.title)
                put("timestamp[$index]", play.playedAtEpochSec.toString())
                put("duration[$index]", play.durationSec.toString())
                play.album?.let { put("album[$index]", it) }
                play.albumArtist?.let { put("albumArtist[$index]", it) }
            }
        }
        return try {
            parseOrThrow(signedPost(params))
            log.info("scrobble.lastfm.batch.ok", mapOf("count" to batch.size))
            batch.map { SubmissionResult(it.queueId, SubmissionOutcome.Success) }
        } catch (expired: ScrobbleError.AuthExpired) {
            log.warning("scrobble.lastfm.batch.authExpired", mapOf("error" to expired.toString()))
            batch.map { SubmissionResult(it.queueId, SubmissionOutcome.AuthExpired) }
        } catch (retryable: ScrobbleError.Retryable) {
            log.warning("scrobble.lastfm.batch.retry", mapOf("reason" to retryable.reason))
            batch.map { SubmissionResult(it.queueId, SubmissionOutcome.Retry(retryable.reason, retryable.retryAfterSec)) }
        } catch (permanent: ScrobbleError.Permanent) {
            log.error("scrobble.lastfm.batch.permanent", mapOf("reason" to permanent.reason))
            batch.map { SubmissionResult(it.queueId, SubmissionOutcome.PermanentFailure(permanent.reason)) }
        }
    }

    /** `auth.getToken`: a temporary token for the browser auth flow. */
    suspend fun requestAuthToken(): String {
        requireConfigured()
        val json = parseOrThrow(signedPost(mapOf("method" to "auth.getToken", "api_key" to config.apiKey)))
        return json["token"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
            ?: throw ScrobbleError.Malformed("missing token")
    }

    /** The browser URL the user opens to authorise [token]. */
    fun authorizationUrl(token: String): HttpUrl =
        AUTH_PAGE.toHttpUrl().newBuilder().addQueryParameter("api_key", config.apiKey).addQueryParameter("token", token).build()

    /** `auth.getSession`: exchange an authorised token for a session key, and store it. */
    suspend fun completeAuth(token: String) {
        requireConfigured()
        val body = parseOrThrow(signedPost(mapOf("method" to "auth.getSession", "api_key" to config.apiKey, "token" to token)))
        val session = body["session"]?.jsonObject ?: throw ScrobbleError.Malformed("missing session")
        val key = session["key"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: throw ScrobbleError.Malformed("missing key")
        val user = session["name"]?.jsonPrimitive?.content.orEmpty()
        tokens.set(TokenKeys.LAST_FM_SESSION, key)
        tokens.set(TokenKeys.LAST_FM_USERNAME, user)
        log.info("scrobble.lastfm.connected", mapOf("user" to user))
    }

    /** Forget the stored session (disconnect). */
    suspend fun disconnect() {
        tokens.clear(TokenKeys.LAST_FM_SESSION)
        tokens.clear(TokenKeys.LAST_FM_USERNAME)
    }

    private suspend fun signedPost(params: Map<String, String>): HttpResponse {
        val signature = LastFmSignature.sign(params, config.sharedSecret)
        val form = FormBody.Builder()
        params.forEach { (key, value) -> form.add(key, value) }
        form.add("api_sig", signature)
        form.add("format", "json")
        return http.execute(Request.Builder().url(config.endpoint).post(form.build()).build())
    }

    private fun parseOrThrow(response: HttpResponse): JsonObject {
        val body = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
        errorFor(response, body)?.let { throw it }
        return body ?: throw ScrobbleError.Malformed("unparseable body")
    }

    /** Map an HTTP response and its parsed body to a typed error, or null when it succeeded. */
    private fun errorFor(response: HttpResponse, body: JsonObject?): ScrobbleError? {
        httpLevelError(response)?.let { return it }
        val code = body?.get("error")?.jsonPrimitive?.int
        val message = body?.get("message")?.jsonPrimitive?.content.orEmpty()
        return when {
            code == null && response.code !in SUCCESS_RANGE -> ScrobbleError.Permanent("http ${response.code}")
            code == null -> null
            code in RETRYABLE_CODES -> ScrobbleError.Retryable("lastfm $code: $message")
            code in AUTH_CODES -> ScrobbleError.AuthExpired(id)
            else -> ScrobbleError.Permanent("lastfm $code: $message")
        }
    }

    private fun httpLevelError(response: HttpResponse): ScrobbleError? = when {
        response.code == TOO_MANY_REQUESTS -> ScrobbleError.Retryable("rate limited", response.retryAfterSec ?: DEFAULT_RETRY_AFTER_SEC)
        response.code in SERVER_ERROR_RANGE -> ScrobbleError.Retryable("http ${response.code}", response.retryAfterSec)
        else -> null
    }

    private suspend fun requireSession(): String {
        requireConfigured()
        return tokens.get(TokenKeys.LAST_FM_SESSION)?.takeIf { it.isNotEmpty() } ?: throw ScrobbleError.NotAuthenticated(id)
    }

    private fun requireConfigured() {
        if (!config.isConfigured) throw ScrobbleError.NotAuthenticated(id)
    }

    private companion object {
        const val AUTH_PAGE = "https://www.last.fm/api/auth/"
        const val TOO_MANY_REQUESTS = 429
        const val DEFAULT_RETRY_AFTER_SEC = 60L
        val SUCCESS_RANGE = 200..299
        val SERVER_ERROR_RANGE = 500..599

        // Last.fm error codes worth retrying: 11 service offline, 16 temporary, 29 rate limit.
        val RETRYABLE_CODES = setOf(11, 16, 29)

        // Auth-class errors: 4 auth failed, 9 invalid session, 10 invalid api key, 13 invalid signature, 14/15 token.
        val AUTH_CODES = setOf(4, 9, 10, 13, 14, 15)
    }
}
