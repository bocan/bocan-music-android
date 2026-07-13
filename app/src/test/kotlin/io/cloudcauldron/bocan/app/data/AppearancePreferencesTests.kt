package io.cloudcauldron.bocan.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AppearancePreferencesTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // One ordered test: the DataStore delegate is a process-wide singleton, so
    // defaults must be observed before any test writes to it.
    @Test
    fun `defaults hold until set values round-trip`() = runTest {
        val prefs = AppearancePreferences(context)

        val defaults = prefs.settings.first()
        assertEquals(ThemeMode.System, defaults.themeMode)
        assertFalse(defaults.dynamicColor)
        assertFalse(defaults.pureBlack)

        prefs.setThemeMode(ThemeMode.Dark)
        prefs.setDynamicColor(true)
        prefs.setPureBlack(true)

        val settings = prefs.settings.first()
        assertEquals(ThemeMode.Dark, settings.themeMode)
        assertTrue(settings.dynamicColor)
        assertTrue(settings.pureBlack)
    }

    @Test
    fun `unknown stored theme key falls back to system`() {
        assertEquals(ThemeMode.System, ThemeMode.fromKey("sepia"))
        assertEquals(ThemeMode.System, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.Dark, ThemeMode.fromKey("dark"))
    }
}
