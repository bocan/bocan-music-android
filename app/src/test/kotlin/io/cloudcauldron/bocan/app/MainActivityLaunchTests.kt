package io.cloudcauldron.bocan.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
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
