package io.cloudcauldron.bocan.app.settings.sections

import io.cloudcauldron.bocan.app.data.ThemeMode

/** The appearance events the screen forwards to the preferences. */
data class AppearanceCallbacks(
    val onSetThemeMode: (ThemeMode) -> Unit,
    val onSetDynamicColor: (Boolean) -> Unit,
    val onSetPureBlack: (Boolean) -> Unit,
    val onBack: () -> Unit
)
