package io.cloudcauldron.bocan.persistence.daos

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.firstList
import io.cloudcauldron.bocan.persistence.fixedClockApplier
import io.cloudcauldron.bocan.persistence.fixtureManifest
import io.cloudcauldron.bocan.persistence.model.AlbumSort
import io.cloudcauldron.bocan.persistence.model.DownloadCounts
import io.cloudcauldron.bocan.persistence.model.TrackSort
import io.cloudcauldron.bocan.persistence.runDbTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LibraryDaoTests {
    @Test
    fun `observeAlbums emits reactively when the applier writes`() = runDbTest { db ->
        db.libraryDao().observeAlbums(AlbumSort.Name).test {
            assertTrue(awaitItem().isEmpty())

            fixedClockApplier(db).apply(fixtureManifest())

            assertEquals(listOf("Loveless", "Souvlaki"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `album sort orders are distinct and stable`() = runDbTest { db ->
        fixedClockApplier(db).apply(fixtureManifest())
        assertEquals(listOf(55L, 56L), db.libraryDao().observeAlbums(AlbumSort.Name).firstList().map { it.id })
        assertEquals(listOf(55L, 56L), db.libraryDao().observeAlbums(AlbumSort.Artist).firstList().map { it.id })
        assertEquals(listOf(56L, 55L), db.libraryDao().observeAlbums(AlbumSort.Year).firstList().map { it.id })
    }

    @Test
    fun `tracks for an album come back in disc and track order`() = runDbTest { db ->
        fixedClockApplier(db).apply(fixtureManifest())
        assertEquals(
            listOf(101L, 102L),
            db.libraryDao().observeTracksForAlbum(55L).firstList().map { it.id }
        )
    }

    @Test
    fun `all-tracks sorts cover title artist and album`() = runDbTest { db ->
        fixedClockApplier(db).apply(fixtureManifest())
        val dao = db.libraryDao()
        assertEquals(
            listOf("Loomer", "Only Shallow", "Souvlaki Space Station", "Souvlaki Space Station (Intro)"),
            dao.observeAllTracks(TrackSort.Title).firstList().map { it.title }
        )
        assertEquals(
            listOf(101L, 102L, 103L, 104L),
            dao.observeAllTracks(TrackSort.Artist).firstList().map { it.id }
        )
        assertEquals(
            listOf(101L, 102L, 103L, 104L),
            dao.observeAllTracks(TrackSort.Album).firstList().map { it.id }
        )
    }

    @Test
    fun `genres are distinct sorted and non-null`() = runDbTest { db ->
        fixedClockApplier(db).apply(fixtureManifest())
        assertEquals(listOf("Shoegaze"), db.libraryDao().observeGenres().firstList())
    }

    @Test
    fun `tracksByIds returns exactly the requested rows`() = runDbTest { db ->
        fixedClockApplier(db).apply(fixtureManifest())
        assertEquals(
            setOf(101L, 103L),
            db.libraryDao().tracksByIds(listOf(101L, 103L, 999L)).map { it.id }.toSet()
        )
    }

    @Test
    fun `download counts follow markDownloaded`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        applier.apply(fixtureManifest())
        assertEquals(DownloadCounts(pending = 4, downloaded = 0, failed = 0), db.libraryDao().observeDownloadCounts().first())

        applier.markDownloaded(listOf(103L), emptyList())

        // The clip inherits its source, so two rows flip together.
        assertEquals(DownloadCounts(pending = 2, downloaded = 2, failed = 0), db.libraryDao().observeDownloadCounts().first())
    }
}
