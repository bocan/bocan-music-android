package io.cloudcauldron.bocan.observability

import android.util.Log
import timber.log.Timber

/**
 * The tree planted in release builds: warnings and errors reach logcat,
 * debug and info are dropped entirely.
 */
class ReleaseLogTree : Timber.DebugTree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN
}
