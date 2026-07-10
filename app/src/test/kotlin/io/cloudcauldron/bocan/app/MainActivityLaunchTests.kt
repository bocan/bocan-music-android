package io.cloudcauldron.bocan.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Robolectric SDK 36+ needs a Java 21 test JVM; the repo standard is JVM 17,
// so the smoke test runs on the newest sandbox that supports Java 17.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MainActivityLaunchTests {
    @Test
    fun `activity reaches resumed state in light mode`() {
        assertLaunches()
    }

    @Test
    @Config(qualifiers = "night")
    fun `activity reaches resumed state in dark mode`() {
        assertLaunches()
    }

    private fun assertLaunches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
