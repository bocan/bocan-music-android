package io.cloudcauldron.bocan.sync.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryBackoffTests {
    @Test
    fun `the first retry waits the shortest delay`() {
        assertEquals(DiscoveryBackoff.FIRST_DELAY_MS, DiscoveryBackoff.delayMillis(0))
    }

    @Test
    fun `each retry doubles the wait`() {
        assertEquals(2_000L, DiscoveryBackoff.delayMillis(1))
        assertEquals(4_000L, DiscoveryBackoff.delayMillis(2))
        assertEquals(8_000L, DiscoveryBackoff.delayMillis(3))
    }

    @Test
    fun `the wait caps and never overflows however long the outage lasts`() {
        assertEquals(DiscoveryBackoff.MAX_DELAY_MS, DiscoveryBackoff.delayMillis(6))
        assertEquals(DiscoveryBackoff.MAX_DELAY_MS, DiscoveryBackoff.delayMillis(100))
        assertEquals(DiscoveryBackoff.MAX_DELAY_MS, DiscoveryBackoff.delayMillis(Long.MAX_VALUE))
    }

    @Test
    fun `every wait stays inside the bounds and never decreases`() {
        var previous = 0L
        (0L..20L).forEach { attempt ->
            val wait = DiscoveryBackoff.delayMillis(attempt)
            assertTrue("attempt $attempt was $wait", wait in DiscoveryBackoff.FIRST_DELAY_MS..DiscoveryBackoff.MAX_DELAY_MS)
            assertTrue("attempt $attempt went backwards", wait >= previous)
            previous = wait
        }
    }
}
