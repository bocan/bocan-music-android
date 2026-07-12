package io.cloudcauldron.bocan.persistence.daos

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.FIXED_NOW
import io.cloudcauldron.bocan.persistence.fixedClockApplier
import io.cloudcauldron.bocan.persistence.fixtureManifest
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.pairedServer
import io.cloudcauldron.bocan.persistence.runDbTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `bulk download-state reset flips every track and episode`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        applier.apply(fixtureManifest())
        applier.markDownloaded(listOf(101L, 102L, 103L, 105L), listOf("e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6"))

        db.syncDao().setAllTrackDownloadStates(DownloadState.Pending)
        db.syncDao().setAllEpisodeDownloadStates(DownloadState.Pending)

        assertTrue(db.syncDao().allTracks().all { it.downloadState == DownloadState.Pending })
        assertTrue(db.syncDao().allEpisodes().all { it.downloadState == DownloadState.Pending })
    }
}
