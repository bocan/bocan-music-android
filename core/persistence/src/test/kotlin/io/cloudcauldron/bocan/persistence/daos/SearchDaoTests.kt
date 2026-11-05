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

@RunWith(AndroidJUnit4::class)
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

    @Test
    fun `word terms only drops terms with no letter or digit`() {
        assertEquals("\"slow\"*", ftsMatchExpression("slow ...", wordTermsOnly = true))
        assertNull(ftsMatchExpression("... !! …", wordTermsOnly = true))
        assertEquals("\"...\"*", ftsMatchExpression("...", wordTermsOnly = false))
    }

    @Test
    fun `search once returns every section capped at the limit`() = runDbTest { db ->
        fixedClockApplier(db).apply(library)
        val stop = db.searchDao().searchOnce("stop", limit = 10)
        assertEquals(setOf(2L, 4L), stop.tracks.map { it.id }.toSet())
        assertEquals(listOf(62L), stop.albums.map { it.id })
        assertEquals(1, db.searchDao().searchOnce("stop", limit = 1).tracks.size)
        assertEquals(listOf(9L), db.searchDao().searchOnce("journey", limit = 10).artists.map { it.id })
    }

    @Test
    fun `search once on punctuation only returns empty and never throws`() = runDbTest { db ->
        fixedClockApplier(db).apply(library)
        assertEquals(SearchResults.EMPTY, db.searchDao().searchOnce("...", limit = 10))
        assertEquals(SearchResults.EMPTY, db.searchDao().searchOnce("… !?", limit = 10))
        assertEquals(listOf(1L), db.searchDao().searchOnce("only ...", limit = 10).tracks.map { it.id })
    }

    @Test
    fun `shows match by title substring with like wildcards taken literally`() = runDbTest { db ->
        fixedClockApplier(db).apply(
            Manifests.manifest(
                podcasts = listOf(
                    Manifests.podcast(1, title = "The Slow Show"),
                    Manifests.podcast(2, title = "100% Pure"),
                    Manifests.podcast(3, title = "100 Pure_Tones")
                )
            )
        )
        val dao = db.searchDao()
        assertEquals(listOf(1L), dao.searchShows("slow", limit = 10).map { it.id })
        assertEquals(listOf(2L), dao.searchShows("100%", limit = 10).map { it.id })
        assertEquals(listOf(3L), dao.searchShows("pure_", limit = 10).map { it.id })
        assertTrue(dao.searchShows("  ", limit = 10).isEmpty())
        assertTrue(dao.searchShows("%", limit = 10).isEmpty())
    }

    @Test
    fun `like pattern escapes wildcards and the escape character`() {
        assertEquals("%a\\%b\\_c\\\\d%", likeContainsPattern(" a%b_c\\d "))
        assertNull(likeContainsPattern("%_"))
    }
}
