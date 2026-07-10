package io.cloudcauldron.bocan.sync.net

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.sync.syncServerEntity
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TrustStoreTests {
    private fun runStoreTest(block: suspend (TrustStore) -> Unit) = runTest {
        val db = BocanDatabase.createInMemory(
            ApplicationProvider.getApplicationContext(),
            StandardTestDispatcher(testScheduler)
        )
        try {
            block(TrustStore(db.syncDao()))
        } finally {
            db.close()
        }
    }

    @Test
    fun `isPaired tracks save and clear`() = runStoreTest { store ->
        store.isPaired.test {
            assertFalse(awaitItem())
            store.save(syncServerEntity(serverId = "mac-1"))
            assertTrue(awaitItem())
            store.clear()
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `current returns the persisted server and null after clear`() = runStoreTest { store ->
        assertNull(store.current())
        store.save(syncServerEntity(serverId = "mac-1", serverName = "Chris's MacBook"))
        assertEquals("Chris's MacBook", store.current()?.serverName)
        store.clear()
        assertNull(store.current())
    }

    @Test
    fun `saving a second server replaces the first`() = runStoreTest { store ->
        store.save(syncServerEntity(serverId = "mac-1"))
        store.save(syncServerEntity(serverId = "mac-2"))
        assertEquals("mac-2", store.current()?.serverId)
    }
}
