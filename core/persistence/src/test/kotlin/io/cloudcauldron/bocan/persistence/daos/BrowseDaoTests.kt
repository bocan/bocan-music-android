package io.cloudcauldron.bocan.persistence.daos

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.persistence.FIXED_NOW
import io.cloudcauldron.bocan.persistence.Manifests
import io.cloudcauldron.bocan.persistence.fixedClockApplier
import io.cloudcauldron.bocan.persistence.model.NameRow
import io.cloudcauldron.bocan.persistence.runDbTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseDaoTests {
    private val library = Manifests.manifest(
        tracks = listOf(
            Manifests.track(1, title = "Alison", artistId = 10, artist = "Slowdive", albumId = 100, album = "Souvlaki"),
            Manifests.track(2, title = "Machine Gun", artistId = 10, artist = "Slowdive", albumId = 100, album = "Souvlaki"),
            Manifests.track(3, title = "Hymne", artistId = 20, artist = "Édith Piaf", albumId = 200, album = "L'Hymne"),
            Manifests.track(4, title = "Teardrop", artistId = 30, artist = "Massive Attack", albumId = 300, album = "Mezzanine")
        )
    )

    private suspend fun BocanDatabase.applyAll(downloaded: List<Long> = listOf(1L, 2L, 3L, 4L)) {
        val applier = fixedClockApplier(this)
        applier.apply(library)
        applier.markDownloaded(downloaded, emptyList())
    }

    @Test
    fun `the name projections return every artist and album`() = runDbTest { db ->
        db.applyAll()
        val dao = db.browseDao()
        assertEquals(
            setOf(NameRow(10, "Slowdive"), NameRow(20, "Édith Piaf"), NameRow(30, "Massive Attack")),
            dao.artistNames().toSet()
        )
        assertEquals(setOf(100L, 200L, 300L), dao.albumNames().map { it.id }.toSet())
    }

    @Test
    fun `members by id come back alphabetically`() = runDbTest { db ->
        db.applyAll()
        val dao = db.browseDao()
        assertEquals(listOf("Massive Attack", "Slowdive"), dao.artistsByIds(listOf(10, 30)).map { it.name })
        assertEquals(listOf("Mezzanine", "Souvlaki"), dao.albumsByIds(listOf(100, 300)).map { it.name })
        assertTrue(dao.artistsByIds(emptyList()).isEmpty())
    }

    @Test
    fun `recently played orders newest first and skips never played and not downloaded`() = runDbTest { db ->
        db.applyAll(downloaded = listOf(1L, 2L, 3L))
        val stats = db.playStatsDao()
        stats.recordPlay(trackId = 1, playedSec = 60, at = FIXED_NOW.minusSeconds(300))
        stats.recordPlay(trackId = 3, playedSec = 60, at = FIXED_NOW.minusSeconds(60))
        // Played but not downloaded: the car cannot play it, so it stays out.
        stats.recordPlay(trackId = 4, playedSec = 60, at = FIXED_NOW)
        // A skip creates a stats row with no lastPlayedAt: never played.
        stats.recordSkip(trackId = 2, atSec = 5)

        assertEquals(listOf(3L, 1L), db.browseDao().recentlyPlayedPage(limit = 50, offset = 0).map { it.id })
        assertEquals(listOf(1L), db.browseDao().recentlyPlayedPage(limit = 1, offset = 1).map { it.id })
    }

    @Test
    fun `an unbounded page returns every row`() = runDbTest { db ->
        db.applyAll()
        val dao = db.browseDao()
        assertEquals(3, dao.artistsPage(limit = Int.MAX_VALUE, offset = 0).size)
        assertEquals(4, dao.recentSongsPage(limit = Int.MAX_VALUE, offset = 0).size)
    }

    @Test
    fun `downloaded track ids come from the shared query`() = runDbTest { db ->
        db.applyAll(downloaded = listOf(2L, 4L))
        assertEquals(listOf(2L, 4L), db.browseDao().downloadedTrackIds())
        assertEquals(listOf(2L, 4L), db.libraryDao().downloadedTrackIds())
    }
}
