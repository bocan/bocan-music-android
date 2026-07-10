package io.cloudcauldron.bocan.persistence.daos

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.firstList
import io.cloudcauldron.bocan.persistence.fixedClockApplier
import io.cloudcauldron.bocan.persistence.fixtureManifest
import io.cloudcauldron.bocan.persistence.model.PlaylistKind
import io.cloudcauldron.bocan.persistence.runDbTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PlaylistDaoTests {
    @Test
    fun `tree ordering puts roots first then children by sort order`() = runDbTest { db ->
        fixedClockApplier(db).apply(fixtureManifest())
        val tree = db.playlistDao().observePlaylistTree().firstList()
        assertEquals(listOf(1L, 3L, 2L), tree.map { it.id })
        assertEquals(PlaylistKind.Folder, tree.first { it.id == 1L }.kind)
        assertEquals(PlaylistKind.Smart, tree.first { it.id == 3L }.kind)
        assertEquals(1L, tree.first { it.id == 2L }.parentId)
    }

    @Test
    fun `playlist members come back in position order reactively`() = runDbTest { db ->
        db.playlistDao().observeTracksIn(2L).test {
            assertTrue(awaitItem().isEmpty())

            fixedClockApplier(db).apply(fixtureManifest())

            assertEquals(listOf(103L, 101L), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
