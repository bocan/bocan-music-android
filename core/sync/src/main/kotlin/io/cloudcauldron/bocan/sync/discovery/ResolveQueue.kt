package io.cloudcauldron.bocan.sync.discovery

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises NsdManager resolve calls. The platform resolver processes exactly
 * one service at a time and fails overlapping calls with FAILURE_ALREADY_ACTIVE,
 * so every resolve runs through here, one at a time, in submission order.
 */
class ResolveQueue {
    private val mutex = Mutex()

    /** Run [block] with no other queued block in flight, returning its result. */
    suspend fun <T> serialize(block: suspend () -> T): T = mutex.withLock { block() }
}
