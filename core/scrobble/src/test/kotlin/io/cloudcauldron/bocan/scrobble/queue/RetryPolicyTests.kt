package io.cloudcauldron.bocan.scrobble.queue

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class RetryPolicyTests {
    @Test
    fun `backoff doubles per attempt`() {
        assertEquals(1L, RetryPolicy.backoffSec(1))
        assertEquals(2L, RetryPolicy.backoffSec(2))
        assertEquals(4L, RetryPolicy.backoffSec(3))
        assertEquals(8L, RetryPolicy.backoffSec(4))
        assertEquals(16L, RetryPolicy.backoffSec(5))
    }

    @Test
    fun `backoff is capped at twenty minutes`() {
        assertEquals(RetryPolicy.MAX_DELAY_SEC, RetryPolicy.backoffSec(40))
    }

    @Test
    fun `a larger retry-after wins but never exceeds the cap`() {
        assertEquals(120L, RetryPolicy.backoffSec(1, retryAfterSec = 120))
        assertEquals(4L, RetryPolicy.backoffSec(3, retryAfterSec = 1))
        assertEquals(RetryPolicy.MAX_DELAY_SEC, RetryPolicy.backoffSec(1, retryAfterSec = 99_999))
    }

    @Test
    fun `a row is exhausted after ten attempts`() {
        assertFalse(RetryPolicy.isExhausted(9))
        assertTrue(RetryPolicy.isExhausted(10))
        assertTrue(RetryPolicy.isExhausted(11))
    }
}
