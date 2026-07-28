package io.cloudcauldron.bocan.sync.discovery

import app.cash.turbine.test
import java.io.IOException
import java.net.InetAddress
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression cover for the two ways a browse session used to die permanently:
 * NsdManager refusing to start (no network yet) and the network going away and
 * coming back. Both left the phone unable to find the Mac until a cold start.
 */
class DiscoveryRestartTests {
    private var starts = 0

    private fun resolved(name: String): ResolvedService =
        ResolvedService(serviceName = name, host = InetAddress.getLoopbackAddress(), port = 8443, txt = emptyMap())

    /** A browse session that fails its first [failures] starts, then stays live like the real one. */
    private fun session(failures: Int = 0, name: String = "Mac A"): Flow<List<ResolvedService>> = flow {
        starts++
        if (starts <= failures) throw IOException("mDNS discovery failed to start: 0")
        emit(listOf(resolved(name)))
        awaitCancellation()
    }

    @Test
    fun `a session that fails to start is retried instead of ending the stream`() = runTest {
        session(failures = 1).restartWhileOnline(flowOf(true), backoff = { 10L }).test {
            assertEquals(emptyList<ResolvedService>(), awaitItem())
            assertEquals(listOf("Mac A"), awaitItem().map { it.serviceName })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, starts)
    }

    @Test
    fun `a session keeps being retried for as long as it keeps failing`() = runTest {
        val attempts = mutableListOf<Long>()
        val backoff = { attempt: Long ->
            attempts += attempt
            10L
        }
        session(failures = 3).restartWhileOnline(flowOf(true), backoff).test {
            repeat(3) { assertEquals(emptyList<ResolvedService>(), awaitItem()) }
            assertEquals(listOf("Mac A"), awaitItem().map { it.serviceName })
            cancelAndIgnoreRemainingEvents()
        }
        // The attempt counter drives the backoff, so each wait is longer than the last.
        assertEquals(listOf(0L, 1L, 2L), attempts)
        assertEquals(4, starts)
    }

    @Test
    fun `losing the network ends the session and regaining it starts a fresh one`() = runTest {
        val online = MutableStateFlow(true)
        session().restartWhileOnline(online, backoff = { 10L }).test {
            assertEquals(listOf("Mac A"), awaitItem().map { it.serviceName })

            online.value = false
            assertEquals(emptyList<ResolvedService>(), awaitItem())

            online.value = true
            assertEquals(listOf("Mac A"), awaitItem().map { it.serviceName })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, starts)
    }

    @Test
    fun `an offline phone reports no macs and never starts a session`() = runTest {
        session().restartWhileOnline(flowOf(false), backoff = { 10L }).test {
            assertEquals(emptyList<ResolvedService>(), awaitItem())
            awaitComplete()
        }
        assertEquals(0, starts)
    }

    @Test
    fun `repeated reports of the same network state do not churn the session`() = runTest {
        val online = MutableStateFlow(true)
        session().restartWhileOnline(online, backoff = { 10L }).test {
            assertEquals(listOf("Mac A"), awaitItem().map { it.serviceName })
            online.value = true
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, starts)
    }
}
