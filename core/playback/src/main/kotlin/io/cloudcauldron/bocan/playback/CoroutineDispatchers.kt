package io.cloudcauldron.bocan.playback

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injected dispatchers so no class references [Dispatchers] statically and tests
 * can substitute a TestDispatcher. Public suspend APIs in this module switch to
 * the right dispatcher internally, so callers never need withContext to call them
 * safely.
 *
 * [main] is the choke point for every MediaController and Player call: Media3
 * requires those on the application main thread.
 */
data class CoroutineDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val main: CoroutineDispatcher = Dispatchers.Main
)
