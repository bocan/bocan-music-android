package io.cloudcauldron.bocan.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "appearance_prefs")

/** The user's theme choice: follow the system, or force light or dark. */
enum class ThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark")
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}

/**
 * One immutable snapshot of every appearance setting the theme needs. Defaults: follow the
 * system light/dark, the Bocan palette (wallpaper colors off), no pure black.
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false
)

/** The appearance preferences the theme and settings read; a fake stands in for tests. */
interface AppearancePreferencesSource {
    val settings: Flow<AppearanceSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setPureBlack(enabled: Boolean)
}

/**
 * DataStore-backed appearance preferences: theme mode (system, light, dark),
 * dynamic Material You color (falls back to the Bocan brand palette when off or
 * unsupported), and the pure-black dark option for OLED screens.
 */
class AppearancePreferences(private val context: Context) : AppearancePreferencesSource {
    override val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.map { prefs ->
        AppearanceSettings(
            themeMode = ThemeMode.fromKey(prefs[THEME_MODE]),
            dynamicColor = prefs[DYNAMIC_COLOR] ?: false,
            pureBlack = prefs[PURE_BLACK] ?: false
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.appearanceDataStore.edit { it[THEME_MODE] = mode.key }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        context.appearanceDataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setPureBlack(enabled: Boolean) {
        context.appearanceDataStore.edit { it[PURE_BLACK] = enabled }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
    }
}
