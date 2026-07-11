package io.cloudcauldron.bocan.scrobble

/**
 * The one typed error hierarchy for the scrobble module. Providers translate HTTP
 * responses into these; the queue and service branch on the type. No bare exceptions
 * cross the module boundary (standards).
 */
sealed class ScrobbleError(message: String) : Exception(message) {
    /** A transient failure (5xx, network, rate limit): retry with backoff. */
    class Retryable(val reason: String, val retryAfterSec: Long? = null) : ScrobbleError(reason)

    /** A permanent failure (a 4xx the service will never accept): dead-letter. */
    class Permanent(val reason: String) : ScrobbleError(reason)

    /** Stored credentials were rejected (401/403): pause the provider, keep the item. */
    class AuthExpired(val provider: String) : ScrobbleError("auth expired: $provider")

    /** No usable credentials: the provider is not connected. */
    class NotAuthenticated(val provider: String) : ScrobbleError("not authenticated: $provider")

    /** A 2xx response that did not contain the expected fields. */
    class Malformed(val reason: String) : ScrobbleError(reason)
}
