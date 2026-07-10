package io.cloudcauldron.bocan.persistence.daos

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.FIXED_NOW
import io.cloudcauldron.bocan.persistence.model.PlayState
import io.cloudcauldron.bocan.persistence.runDbTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class EpisodeStateDaoTests {
    @Test
    fun `updatePosition creates or advances in-progress state`() = runDbTest { db ->
        val dao = db.episodeStateDao()
        dao.updatePosition("ep-a", 1000, at = FIXED_NOW.minusSeconds(60))
        dao.updatePosition("ep-a", 2000, at = FIXED_NOW)

        val state = dao.state("ep-a")
        assertEquals(2000L, state?.playPositionMs)
        assertEquals(PlayState.InProgress, state?.playState)
        assertEquals(FIXED_NOW, state?.lastPlayedAt)
        assertNull(state?.completedAt)
    }

    @Test
    fun `markPlayed completes the episode and keeps the position`() = runDbTest { db ->
        val dao = db.episodeStateDao()
        dao.updatePosition("ep-a", 3_500_000, at = FIXED_NOW.minusSeconds(60))
        dao.markPlayed("ep-a", at = FIXED_NOW)

        val state = dao.state("ep-a")
        assertEquals(PlayState.Played, state?.playState)
        assertEquals(FIXED_NOW, state?.completedAt)
        assertEquals(3_500_000L, state?.playPositionMs)
    }

    @Test
    fun `speed override persists independently of progress`() = runDbTest { db ->
        val dao = db.episodeStateDao()
        dao.updatePosition("ep-a", 1000, at = FIXED_NOW)
        dao.setSpeedOverride("ep-a", 1.5)

        assertEquals(1.5, dao.state("ep-a")?.speedOverride)
        dao.setSpeedOverride("ep-a", null)
        assertNull(dao.state("ep-a")?.speedOverride)
    }

    @Test
    fun `observeState emits reactively`() = runDbTest { db ->
        val dao = db.episodeStateDao()
        dao.observeState("ep-a").test {
            assertNull(awaitItem())

            dao.updatePosition("ep-a", 1000, at = FIXED_NOW)

            assertEquals(1000L, awaitItem()?.playPositionMs)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
