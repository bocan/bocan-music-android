package io.cloudcauldron.bocan.persistence.daos

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.FIXED_NOW
import io.cloudcauldron.bocan.persistence.Manifests
import io.cloudcauldron.bocan.persistence.entities.PlayStatsEntity
import io.cloudcauldron.bocan.persistence.fixedClockApplier
import io.cloudcauldron.bocan.persistence.runDbTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PlayStatsDaoTests {
    @Test
    fun `recordPlay accumulates counts durations and recency`() = runDbTest { db ->
        val dao = db.playStatsDao()
        dao.recordPlay(1L, playedSec = 200, at = FIXED_NOW.minusSeconds(600))
        dao.recordPlay(1L, playedSec = 100, at = FIXED_NOW)

        val stats = dao.stats(1L)
        assertEquals(2L, stats?.playCount)
        assertEquals(300L, stats?.playDurationTotalSec)
        assertEquals(FIXED_NOW, stats?.lastPlayedAt)
    }

    @Test
    fun `recordSkip counts skips and remembers the skip point`() = runDbTest { db ->
        val dao = db.playStatsDao()
        dao.recordSkip(1L, atSec = 12)
        dao.recordSkip(1L, atSec = 30)

        val stats = dao.stats(1L)
        assertEquals(2L, stats?.skipCount)
        assertEquals(30, stats?.skipAfterSeconds)
        assertEquals(0L, stats?.playCount)
    }

    @Test
    fun `observeStats emits reactively on writes`() = runDbTest { db ->
        val dao = db.playStatsDao()
        dao.observeStats(1L).test {
            assertNull(awaitItem())

            dao.recordPlay(1L, playedSec = 10, at = FIXED_NOW)

            assertEquals(1L, awaitItem()?.playCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `prune removes only stale orphans`() = runDbTest { db ->
        fixedClockApplier(db).apply(Manifests.manifest(tracks = listOf(Manifests.track(1))))
        val dao = db.playStatsDao()
        dao.recordPlay(1L, playedSec = 10, at = FIXED_NOW)

        // Orphan with old history, orphan with recent history, and one never played.
        dao.recordPlay(90L, playedSec = 10, at = FIXED_NOW.minusSeconds(90L * 24 * 3600))
        dao.recordPlay(91L, playedSec = 10, at = FIXED_NOW.minusSeconds(3600))
        dao.upsert(PlayStatsEntity(trackId = 92L))

        dao.pruneOrphanedOlderThan(days = 30, now = FIXED_NOW)

        assertEquals(1L, dao.stats(1L)?.playCount)
        assertNull(dao.stats(90L))
        assertEquals(1L, dao.stats(91L)?.playCount)
        assertNull(dao.stats(92L))
    }
}
