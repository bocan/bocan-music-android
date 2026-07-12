package io.cloudcauldron.bocan.app.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import io.cloudcauldron.bocan.app.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResolutionTests {
    @Test
    fun `system mode follows the system, forced modes ignore it`() {
        assertTrue(resolvesToDark(ThemeMode.System, systemDark = true))
        assertFalse(resolvesToDark(ThemeMode.System, systemDark = false))
        assertFalse(resolvesToDark(ThemeMode.Light, systemDark = true))
        assertTrue(resolvesToDark(ThemeMode.Dark, systemDark = false))
    }

    @Test
    fun `pure black flips backgrounds to black and keeps accents`() {
        val base = darkColorScheme(primary = Color(0xFF4C8DFF), background = Color(0xFF1A1024))

        val black = base.pureBlack()

        assertEquals(Color.Black, black.background)
        assertEquals(Color.Black, black.surface)
        assertEquals(base.primary, black.primary)
        assertEquals(base.onSurface, black.onSurface)
    }
}
