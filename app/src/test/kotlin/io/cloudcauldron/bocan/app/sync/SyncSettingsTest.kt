package io.cloudcauldron.bocan.app.sync

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SyncSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `defaults are discovery and periodic on, charging-only off`() {
        val settings = SyncSettings(context)

        assertTrue(settings.syncOnDiscovery.value)
        assertTrue(settings.periodicSync.value)
        assertFalse(settings.chargingOnly.value)
    }

    @Test
    fun `setters persist across instances`() {
        SyncSettings(context).apply {
            setSyncOnDiscovery(false)
            setPeriodicSync(false)
            setChargingOnly(true)
        }

        val reloaded = SyncSettings(context)
        assertFalse(reloaded.syncOnDiscovery.value)
        assertFalse(reloaded.periodicSync.value)
        assertTrue(reloaded.chargingOnly.value)
    }

    @Test
    fun `legacy single auto-sync flag seeds both new toggles`() {
        context.getSharedPreferences("bocan.sync.settings", Context.MODE_PRIVATE).edit {
            putBoolean("autoSync", false)
        }

        val settings = SyncSettings(context)

        assertFalse(settings.syncOnDiscovery.value)
        assertFalse(settings.periodicSync.value)
    }
}
