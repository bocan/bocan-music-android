package io.cloudcauldron.bocan.app.library

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** The A to Z rail (Songs, Artists, Genres): what it indexes, where a letter goes, and which letters fit. */
class AlphabetRailTests {
    @Test
    fun `an accented first letter is indexed under its plain letter`() {
        assertEquals(mapOf('E' to 0, 'N' to 1), railIndex(listOf("Édith Marceau", "ñandú")))
        // Ø is a letter of its own, not O with a mark, so it has no place on an A to Z rail.
        assertEquals(emptyMap(), railIndex(listOf("Øresund")))
    }

    @Test
    fun `the artist rail follows the list order, a leading The included`() {
        // The fixture's artists in the order the Artists tab shows them (name COLLATE NOCASE).
        val artists = listOf(
            "!Lumen", "2 Bridges", "Arctic Owls", "Aurora Lane", "Cassia Moor", "Dunmore Tide", "Fennel & Finch",
            "Grey Heron Trio", "Kestrel", "Mossgiel", "Saltmarsh Choir", "The Bellwethers", "Zephyr Lines", "Édith Marceau"
        )
        val index = railIndex(artists)
        assertEquals(2, index['A'])
        assertEquals(11, index['T'])
        assertEquals(13, index['E'])
        assertNull(index['B'])
        // B has no artist in the list, so it goes to the next letter that has one.
        assertEquals(4, railTarget(index, 'B'))
    }

    @Test
    fun `the index is the first row of each letter of the sort key, in list order`() {
        val index = railIndex(listOf("Breakwater 1", "breakwater 2", "Crossings 1", "Signal 1", "signal 2"))
        assertEquals(mapOf('B' to 0, 'C' to 2, 'S' to 3), index)
    }

    @Test
    fun `keys that do not start with a letter are not on the rail`() {
        assertEquals(mapOf('A' to 2), railIndex(listOf("1999", "(intro)", "Alpha", "")))
    }

    @Test
    fun `under the artist sort the rail follows the artist, not the title`() {
        // A list sorted by artist: titles in no order the rail could use.
        val rows = listOf("Zebra Song" to "Aurora Lane", "Apple" to "Aurora Lane", "Moon" to "Bellwethers", "Alpha" to "Corrie")
        val byArtist = railIndex(rows.map { it.second })
        assertEquals(mapOf('A' to 0, 'B' to 2, 'C' to 3), byArtist)
        // The bug this replaces: indexing titles in an artist-sorted list sends 'A' to row 1, mid "Aurora Lane".
        assertEquals(1, railIndex(rows.map { it.first })['A'])
    }

    @Test
    fun `a letter with no rows goes to the next letter that has some`() {
        val index = mapOf('B' to 0, 'C' to 2, 'S' to 3)
        assertEquals(0, railTarget(index, 'A'))
        assertEquals(3, railTarget(index, 'D'))
        assertEquals(3, railTarget(index, 'S'))
    }

    @Test
    fun `a letter past the last one with rows goes to that last letter`() {
        assertEquals(3, railTarget(mapOf('B' to 0, 'S' to 3), 'Z'))
        assertNull(railTarget(emptyMap(), 'A'))
    }

    @Test
    fun `all 26 letters show when they fit`() {
        assertEquals(('A'..'Z').toList(), railLetters(capacity = 26))
        assertEquals(('A'..'Z').toList(), railLetters(capacity = 40))
    }

    @Test
    fun `when they do not fit, every other or every third letter shows, starting at A`() {
        assertEquals("ACEGIKMOQSUWY", railLetters(capacity = 13).joinToString(""))
        assertEquals("ADGJMPSVY", railLetters(capacity = 12).joinToString(""))
        assertEquals(listOf('A'), railLetters(capacity = 0))
    }

    @Test
    fun `a drag along the rail maps its position to every letter`() {
        assertEquals('A', railLetterAt(0f))
        assertEquals('A', railLetterAt(-0.2f))
        assertEquals('N', railLetterAt(0.5f))
        assertEquals('Z', railLetterAt(0.999f))
        assertEquals('Z', railLetterAt(1.4f))
    }
}
