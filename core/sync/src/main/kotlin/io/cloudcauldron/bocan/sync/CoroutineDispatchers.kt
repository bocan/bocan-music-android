package io.cloudcauldron.bocan.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injected dispatchers so no class references [Dispatchers] statically and tests
 * can substitute a TestDispatcher. Public suspend APIs in this module switch to
 * [io] internally, so callers never need withContext to call them safely.
 */
data class CoroutineDispatchers(val io: CoroutineDispatcher = Dispatchers.IO, val default: CoroutineDispatcher = Dispatchers.Default)
