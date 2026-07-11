package io.cloudcauldron.bocan.app.playback

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Optionally resumes playback when headphones (wired, USB, or Bluetooth) reconnect after
 * being unplugged, mirroring the "resume on reconnect" setting (default off). It only asks
 * the caller to resume; the caller resumes solely if playback is paused with content, and
 * through the already-connected session controller, so the foreground-start rules are
 * respected (no service is started from here).
 */
class HeadphoneReconnectMonitor(
    private val audioManager: AudioManager?,
    private val resumeEnabled: suspend () -> Boolean,
    private val onReconnect: () -> Unit,
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers
) {
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.any(::isHeadset)) {
                scope.launch {
                    if (resumeEnabled()) withContext(dispatchers.main) { onReconnect() }
                }
            }
        }
    }

    fun start() {
        audioManager?.registerAudioDeviceCallback(callback, null)
    }

    fun stop() {
        audioManager?.unregisterAudioDeviceCallback(callback)
    }

    private fun isHeadset(device: AudioDeviceInfo): Boolean = device.isSink && device.type in HEADSET_TYPES

    private companion object {
        val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        )
    }
}
