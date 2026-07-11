package io.cloudcauldron.bocan.scrobble.net

import io.cloudcauldron.bocan.scrobble.CoroutineDispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A provider HTTP response reduced to what the providers branch on. */
data class HttpResponse(val code: Int, val body: String, val retryAfterSec: Long?)

/**
 * A thin suspend wrapper over OkHttp for the scrobble providers. Unlike the sync client
 * this uses the system trust store: scrobble services are public HTTPS endpoints, not the
 * pinned mutual-TLS Mac. Blocking OkHttp calls run on the IO dispatcher; the response body
 * is fully read and the call closed before returning.
 */
class ScrobbleHttp(private val client: OkHttpClient, private val dispatchers: CoroutineDispatchers) {
    suspend fun execute(request: Request): HttpResponse = withContext(dispatchers.io) {
        client.newCall(request).execute().use { response ->
            HttpResponse(
                code = response.code,
                body = response.body.string(),
                retryAfterSec = response.header("Retry-After")?.toLongOrNull()
            )
        }
    }
}
