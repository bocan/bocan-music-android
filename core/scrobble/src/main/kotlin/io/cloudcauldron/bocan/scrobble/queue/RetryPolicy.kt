package io.cloudcauldron.bocan.scrobble.queue

/**
 * Exponential backoff for retryable submission failures, the phase 09 schedule: 1 s,
 * 2 s, 4 s, 8 s, ... doubling per attempt, capped at 20 minutes, and a row that has
 * failed [MAX_ATTEMPTS] times is dead-lettered. Pure integer math so the schedule is
 * unit tested against virtual time.
 *
 * (This is the Android contract from the phase's Definitions section; it intentionally
 * differs from the Mac's `RetryPolicy`, which uses a 30 s base and a 20-attempt cap.)
 */
object RetryPolicy {
    const val BASE_DELAY_SEC = 1L
    const val MAX_DELAY_SEC = 20L * 60L
    const val MAX_ATTEMPTS = 10

    /**
     * Seconds to wait before the next attempt, given the number of attempts already
     * made ([attempts] >= 1). A provider-supplied [retryAfterSec] (from a Retry-After
     * header) takes precedence when larger, so we never hammer a rate-limited service.
     */
    fun backoffSec(attempts: Int, retryAfterSec: Long? = null): Long {
        val exponent = (attempts - 1).coerceIn(0, MAX_SHIFT)
        val doubled = (BASE_DELAY_SEC shl exponent).coerceIn(BASE_DELAY_SEC, MAX_DELAY_SEC)
        return maxOf(doubled, retryAfterSec ?: 0L).coerceAtMost(MAX_DELAY_SEC)
    }

    /** True once a row has used up its retries and should be dead-lettered. */
    fun isExhausted(attempts: Int): Boolean = attempts >= MAX_ATTEMPTS

    // Guard the shift against overflow; 62 is far beyond MAX_ATTEMPTS but keeps this total.
    private const val MAX_SHIFT = 62
}
