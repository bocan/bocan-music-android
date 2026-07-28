package io.cloudcauldron.bocan.sync.discovery

/**
 * How long to wait before restarting mDNS discovery after it fails. Doubles from
 * [FIRST_DELAY_MS] and caps at [MAX_DELAY_MS]: a phone whose Wi-Fi is still
 * coming up recovers within a second or two, while an NsdManager that keeps
 * refusing is retried once a minute instead of in a hot loop.
 */
internal object DiscoveryBackoff {
    const val FIRST_DELAY_MS = 1_000L
    const val MAX_DELAY_MS = 60_000L

    /** The wait before retry number [attempt], counting from zero. */
    fun delayMillis(attempt: Long): Long {
        if (attempt >= MAX_DOUBLINGS) return MAX_DELAY_MS
        val doubled = FIRST_DELAY_MS shl attempt.coerceAtLeast(0).toInt()
        return doubled.coerceIn(FIRST_DELAY_MS, MAX_DELAY_MS)
    }

    // 1_000 shl 6 is 64_000, already past the cap, so nothing above this can overflow.
    private const val MAX_DOUBLINGS = 6L
}
