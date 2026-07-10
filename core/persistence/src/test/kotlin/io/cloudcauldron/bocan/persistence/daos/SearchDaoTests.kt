package io.cloudcauldron.bocan.persistence.daos

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.persistence.Manifests
import io.cloudcauldron.bocan.persistence.fixedClockApplier
import io.cloudcauldron.bocan.persistence.model.SearchResults
import io.cloudcauldron.bocan.persistence.runDbTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SearchDaoTests {
    private val library = Manifests.manifest(
        tracks = listOf(
            Manifests.track(1, title = "Only Shallow", artistId = 7, artist = "My Bloody Valentine", albumId = 55, album = "Loveless"),
            Manifests.track(2, title = "Don't Stop Believin'", artistId = 9, artist = "Journey", albumId = 60, album = "Escape"),
            Manifests.track(3, title = "Rockin' in the Free World", artistId = 10, artist = "Neil Young", albumId = 61, album = "Freedom"),
            Manifests.track(
                4,
                title = "Stop Making Sense",
                artistId = 11,
                artist = "Talking Heads",
                albumId = 62,
                album = "Stop Making Sense"
            )
        )
    )

    private suspend fun BocanDatabase.search(query: String): SearchResults = searchDao().search(query).first()

    @Test
    fun `prefix match finds tracks`() = runDbTest { db ->
        fixedClockApplier(db).apply(library)
        assertEquals(listOf(1L), db.search("onl").tracks.map { it.id })
        assertEquals(listOf(1L), db.search("shal").tracks.map { it.id })
    }

    @Test
    fun `multiple terms combine as AND`() = runDbTest { db ->
        fixedClockApplier(db).apply(library)
        assertEquals(listOf(1L), db.search("only shallow").tracks.map { it.id })
        assertTrue(db.search("only sense").tracks.isEmpty())
    }

    @Test
    fun `apostrophes and embedded quotes are safe`() = runDbTest { db ->
        fixedClockApplier(db).apply(library)
        assertEquals(listOf(2L), db.search("don't").tracks.map { it.id })
        assertEquals(listOf(2L), db.search("\"don't stop\"").tracks.map { it.id })
    }

    @Test
    fun `fts operators in input are matched literally not interpreted`() = runDbTest { db ->
        fixedClockApplier(db).apply(library)
        assertEquals(listOf(3L), db.search("rock*").tracks.map { it.id })
        assertTrue(db.search("NEAR(only").tracks.isEmpty())
        assertTrue(db.search("only AND").tracks.isEmpty())
    }

    @Test
    fun `empty and blank queries return empty results`() = runDbTest { db ->
        fixedClockApplier(db).apply(library)
        assertEquals(SearchResults.EMPTY, db.search(""))
        assertEquals(SearchResults.EMPTY, db.search("   "))
    }

    @Test
    fun `album and artist matches come back grouped`() = runDbTest { db ->
        fixedClockApplier(db).apply(library)
        val byAlbum = db.search("loveless")
        assertEquals(listOf(55L), byAlbum.albums.map { it.id })
        val byArtist = db.search("journey")
        assertEquals(listOf(9L), byArtist.artists.map { it.id })
    }

    @Test
    fun `match expression quotes each term with a prefix star`() {
        assertEquals("\"only\"*", ftsMatchExpression("only"))
        assertEquals("\"only\"* \"shallow\"*", ftsMatchExpression(" only  shallow "))
        assertEquals("\"don't\"*", ftsMatchExpression("don't"))
        assertEquals("\"\"\"quoted\"\"\"*", ftsMatchExpression("\"quoted\""))
        assertNull(ftsMatchExpression("   "))
    }
}
