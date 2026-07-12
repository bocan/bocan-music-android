package io.cloudcauldron.bocan.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.cloudcauldron.bocan.app.data.AppearanceSettings
import io.cloudcauldron.bocan.app.data.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = AccentLight,
    background = LightSurface,
    surface = LightSurface,
    surfaceVariant = LightSurfaceHigh,
    onBackground = InkOnLight,
    onSurface = InkOnLight
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    background = NightSurface,
    surface = NightSurface,
    surfaceVariant = NightSurfaceHigh,
    onBackground = MistOnDark,
    onSurface = MistOnDark
)

/** Whether the app renders dark for [mode], honouring the system when asked to. */
fun resolvesToDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * Turn a dark scheme pure black for OLED screens. Backgrounds and surfaces go to
 * true black while every content and accent role keeps its contrast-checked value,
 * so primary stays readable (black only raises contrast against light content).
 */
fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black
)

/**
 * Material 3 theme driven by the user's appearance settings: theme mode
 * (system, light, dark), dynamic color (Material You on API 31+, off falls back
 * to the Bocan brand palette), and pure-black dark for OLED.
 */
@Composable
fun BocanTheme(appearance: AppearanceSettings = AppearanceSettings(), content: @Composable () -> Unit) {
    val darkTheme = resolvesToDark(appearance.themeMode, isSystemInDarkTheme())
    val dynamic = appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        dynamic -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = if (darkTheme && appearance.pureBlack) base.pureBlack() else base

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BocanTypography,
        content = content
    )
}
