package io.cloudcauldron.bocan.app.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the one sync engine for the paired certificate, keyed by its fingerprint.
 *
 * Callers race: pairing finishes and starts a forced sync while discovery sees the
 * newly paired Mac and starts another. Unguarded, both found the slot empty and each
 * built an engine. Each engine coalesces only its own runs, so two runs downloaded
 * the same artwork beside each other and one crashed on the other's rename. The lock
 * makes the check and the build one step, so every caller for a key gets the same
 * instance.
 *
 * When the key changes (the phone re-pairs with another certificate), the old
 * instance goes to [onReplaced] so its run can stop instead of syncing on for a Mac
 * the phone no longer trusts.
 */
internal class EngineSlot<T : Any>(private val onReplaced: (T) -> Unit = {}) {
    private val lock = Mutex()

    @Volatile private var held: Pair<String, T>? = null

    /** The instance for the current key, or null before the first [obtain] or after [clear]. */
    val current: T? get() = held?.second

    /** The instance for [key], built with [build] only if the slot holds none for that key. */
    suspend fun obtain(key: String, build: suspend () -> T): T = lock.withLock {
        held?.takeIf { it.first == key }?.second ?: build().also { fresh ->
            held?.second?.let(onReplaced)
            held = key to fresh
        }
    }

    /** Forget the held instance (unpair), so the next [obtain] builds afresh. */
    suspend fun clear() {
        lock.withLock { held = null }
    }
}
