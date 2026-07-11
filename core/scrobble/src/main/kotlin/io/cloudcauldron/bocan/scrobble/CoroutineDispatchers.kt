package io.cloudcauldron.bocan.scrobble

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injected dispatchers so no class references [Dispatchers] statically and tests can
 * substitute a TestDispatcher. Public suspend APIs switch to the right dispatcher
 * internally; callers never need withContext. Scrobbling never runs on the audio
 * thread: the service hops here immediately (phase 09 gotcha).
 */
data class CoroutineDispatchers(val io: CoroutineDispatcher = Dispatchers.IO, val default: CoroutineDispatcher = Dispatchers.Default)
