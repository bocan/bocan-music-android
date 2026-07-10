package io.cloudcauldron.bocan.sync.discovery

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveQueueTests {
    @Test
    fun `serialize runs at most one block at a time`() = runTest {
        val queue = ResolveQueue()
        var active = 0
        var maxActive = 0
        val jobs = (1..20).map {
            launch {
                queue.serialize {
                    active++
                    maxActive = maxOf(maxActive, active)
                    yield()
                    active--
                }
            }
        }
        jobs.joinAll()
        assertEquals(1, maxActive)
    }

    @Test
    fun `serialize returns the block result`() = runTest {
        val queue = ResolveQueue()
        assertEquals(42, queue.serialize { 42 })
    }
}
