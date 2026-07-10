package io.cloudcauldron.bocan.persistence.daos

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.FIXED_NOW
import io.cloudcauldron.bocan.persistence.fixedClockApplier
import io.cloudcauldron.bocan.persistence.fixtureManifest
import io.cloudcauldron.bocan.persistence.pairedServer
import io.cloudcauldron.bocan.persistence.runDbTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SyncDaoTests {
    @Test
    fun `observeServer emits reactively across pair and unpair`() = runDbTest { db ->
        val dao = db.syncDao()
        dao.observeServer().test {
            assertNull(awaitItem())

            dao.replaceServer(pairedServer())
            assertEquals("test-server", awaitItem()?.serverId)

            dao.clearServer()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replacing the server keeps a single row`() = runDbTest { db ->
        val dao = db.syncDao()
        dao.replaceServer(pairedServer())
        dao.replaceServer(pairedServer().copy(serverId = "another-mac", pairedAt = FIXED_NOW))

        assertEquals("another-mac", dao.server()?.serverId)
    }

    @Test
    fun `known artwork hashes union all referencing tables`() = runDbTest { db ->
        fixedClockApplier(db).apply(fixtureManifest())
        val known = db.syncDao().knownArtworkHashes().toSet()
        assertEquals(3, known.size)
    }
}
