package io.cloudcauldron.bocan.scrobble.providers

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.scrobble.AuthState
import io.cloudcauldron.bocan.scrobble.PlayEvent
import io.cloudcauldron.bocan.scrobble.ScrobbleError
import io.cloudcauldron.bocan.scrobble.SubmissionOutcome
import io.cloudcauldron.bocan.scrobble.SubmissionResult
import io.cloudcauldron.bocan.scrobble.auth.TokenStore
import io.cloudcauldron.bocan.scrobble.net.HttpResponse
import io.cloudcauldron.bocan.scrobble.net.ScrobbleHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The shared implementation for the ListenBrainz-compatible services: ListenBrainz itself
 * and Rocksky, which speaks the same `submit-listens` JSON protocol. Subclasses supply
 * only the id, display name, base URL, and token key; everything else (the listen payload,
 * the Bearer-token POST, the error mapping) is identical.
 *
 * The token is a Bearer credential (`Authorization: Token <token>`). A now-playing call is
 * best-effort. A batch submit returns one result per play; 401/403 pauses the provider
 * (auth expired) without dropping items, 429 and 5xx retry, other 4xx are permanent.
 */
abstract class ListenBrainzCompatibleProvider(
    final override val id: String,
    final override val displayName: String,
    private val baseUrl: HttpUrl,
    private val tokenKey: String,
    private val tokens: TokenStore,
    private val http: ScrobbleHttp
) : ScrobbleProvider {
    private val log = AppLog.forCategory(LogCategory.Scrobble)

    override val authState: Flow<AuthState> = tokens.observe(tokenKey).map { token ->
        if (token.isNullOrEmpty()) AuthState.Disconnected else AuthState.Connected(username = null)
    }

    override suspend fun isAuthenticated(): Boolean = !tokens.get(tokenKey).isNullOrEmpty()

    override suspend fun updateNowPlaying(play: PlayEvent) {
        val token = tokens.get(tokenKey)
        if (token.isNullOrEmpty()) throw ScrobbleError.NotAuthenticated(id)
        ensureOk(postListens(token, listenType = "playing_now", plays = listOf(play)))
        log.debug("scrobble.$id.nowplaying.ok", mapOf("title" to play.title))
    }

    @Suppress("ReturnCount") // guard clauses for empty batch and missing token, then the submit path
    override suspend fun scrobble(batch: List<PlayEvent>): List<SubmissionResult> {
        if (batch.isEmpty()) return emptyList()
        val token = tokens.get(tokenKey)
        if (token.isNullOrEmpty()) return batch.map { SubmissionResult(it.queueId, SubmissionOutcome.AuthExpired) }
        return try {
            ensureOk(postListens(token, if (batch.size == 1) "single" else "import", batch))
            log.info("scrobble.$id.batch.ok", mapOf("count" to batch.size))
            batch.map { SubmissionResult(it.queueId, SubmissionOutcome.Success) }
        } catch (expired: ScrobbleError.AuthExpired) {
            log.warning("scrobble.$id.batch.authExpired", mapOf("error" to expired.toString()))
            batch.map { SubmissionResult(it.queueId, SubmissionOutcome.AuthExpired) }
        } catch (retryable: ScrobbleError.Retryable) {
            log.warning("scrobble.$id.batch.retry", mapOf("reason" to retryable.reason))
            batch.map { SubmissionResult(it.queueId, SubmissionOutcome.Retry(retryable.reason, retryable.retryAfterSec)) }
        } catch (permanent: ScrobbleError.Permanent) {
            log.error("scrobble.$id.batch.permanent", mapOf("reason" to permanent.reason))
            batch.map { SubmissionResult(it.queueId, SubmissionOutcome.PermanentFailure(permanent.reason)) }
        }
    }

    private suspend fun postListens(token: String, listenType: String, plays: List<PlayEvent>): HttpResponse {
        val body = buildJsonObject {
            put("listen_type", listenType)
            putJsonArray("payload") {
                plays.forEach { play ->
                    addJsonObject {
                        if (listenType != "playing_now") put("listened_at", play.playedAtEpochSec)
                        putJsonObject("track_metadata") {
                            put("artist_name", play.artist)
                            put("track_name", play.title)
                            play.album?.let { put("release_name", it) }
                            putJsonObject("additional_info") {
                                put("media_player", CLIENT_NAME)
                                put("submission_client", CLIENT_NAME)
                                if (play.durationSec > 0) put("duration_ms", play.durationSec * MS_PER_SECOND)
                            }
                        }
                    }
                }
            }
        }.toString()

        val url = baseUrl.newBuilder().addPathSegments("1/submit-listens").build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Token $token")
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return http.execute(request)
    }

    private fun ensureOk(response: HttpResponse) {
        errorFor(response)?.let { throw it }
    }

    /** Map a response to a typed error, or null when it succeeded. */
    private fun errorFor(response: HttpResponse): ScrobbleError? = when (response.code) {
        in SUCCESS_RANGE -> null
        UNAUTHORIZED, FORBIDDEN -> ScrobbleError.AuthExpired(id)
        TOO_MANY_REQUESTS -> ScrobbleError.Retryable("rate limited", response.retryAfterSec ?: DEFAULT_RETRY_AFTER_SEC)
        in SERVER_ERROR_RANGE -> ScrobbleError.Retryable("http ${response.code}", response.retryAfterSec)
        else -> ScrobbleError.Permanent("http ${response.code}: ${response.body.take(BODY_SNIPPET)}")
    }

    private companion object {
        const val CLIENT_NAME = "Bocan"
        const val MS_PER_SECOND = 1000
        const val BODY_SNIPPET = 200
        const val DEFAULT_RETRY_AFTER_SEC = 60L
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val TOO_MANY_REQUESTS = 429
        val SUCCESS_RANGE = 200..299
        val SERVER_ERROR_RANGE = 500..599
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
