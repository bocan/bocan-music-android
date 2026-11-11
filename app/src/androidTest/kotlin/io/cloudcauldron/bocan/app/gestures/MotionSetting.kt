package io.cloudcauldron.bocan.app.gestures

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The system animator scale, which the app reads as reduced motion when it is 0 (Motion.kt).
 * It is a global device setting, not app data, so the Orchestrator's clearing does not reset
 * it: [set] remembers what the device had and [restore] (from an `@After`) puts it back, so a
 * test never leaves the next one, or a developer's emulator, in the wrong mode.
 */
class MotionSetting {
    private var original: String? = null

    /** Animations off ([reduced]) or at normal speed. Set before the screen under test is composed. */
    fun set(reduced: Boolean) {
        if (original == null) original = shell("settings get global $KEY")
        shell("settings put global $KEY ${if (reduced) "0" else "1"}")
    }

    fun restore() {
        val before = original ?: return
        // "null" means the device never had the key set: delete it rather than writing a value.
        shell(if (before == "null") "settings delete global $KEY" else "settings put global $KEY $before")
        original = null
    }

    private fun shell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString().trim() }
    }

    private companion object {
        const val KEY = "animator_duration_scale"
    }
}
