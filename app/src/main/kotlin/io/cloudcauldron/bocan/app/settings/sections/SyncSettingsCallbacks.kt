package io.cloudcauldron.bocan.app.settings.sections

/** The events the stateless sync settings screen forwards. */
data class SyncSettingsCallbacks(
    val onSyncNow: () -> Unit,
    val onCancel: () -> Unit,
    val onSetSyncOnDiscovery: (Boolean) -> Unit,
    val onSetPeriodicSync: (Boolean) -> Unit,
    val onSetChargingOnly: (Boolean) -> Unit,
    val onUnpair: () -> Unit,
    val onRemoveAllMedia: () -> Unit,
    val onPair: () -> Unit,
    val onBack: () -> Unit
)
