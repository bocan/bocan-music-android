package io.cloudcauldron.bocan.playback.browse

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class LetterBucketsTests {
    @Test
    fun `a leading the or a is skipped when a word follows`() {
        assertEquals("B", LetterBuckets.keyFor("The Beatles"))
        assertEquals("T", LetterBuckets.keyFor("A Tribe Called Quest"))
        assertEquals("T", LetterBuckets.keyFor("the the"))
    }

    @Test
    fun `an article alone is its own name`() {
        assertEquals("A", LetterBuckets.keyFor("A"))
        assertEquals("T", LetterBuckets.keyFor("The"))
        assertEquals("A", LetterBuckets.keyFor("Abba"))
        assertEquals("T", LetterBuckets.keyFor("Theatre of Tragedy"))
    }

    @Test
    fun `accents fold to their base letter`() {
        assertEquals("E", LetterBuckets.keyFor("Édith Piaf"))
        assertEquals("E", LetterBuckets.keyFor("Édith Piaf"))
        assertEquals("O", LetterBuckets.keyFor("ólafur Arnalds"))
    }

    @Test
    fun `lower case names land in the upper case bucket`() {
        assertEquals("M", LetterBuckets.keyFor("mogwai"))
    }

    @Test
    fun `digits share one bucket`() {
        assertEquals(LetterBuckets.DIGITS, LetterBuckets.keyFor("2Pac"))
        assertEquals(LetterBuckets.DIGITS, LetterBuckets.keyFor("10,000 Maniacs"))
    }

    @Test
    fun `anything else goes to the other bucket`() {
        assertEquals(LetterBuckets.OTHER, LetterBuckets.keyFor("…And You Will Know Us by the Trail of Dead"))
        assertEquals(LetterBuckets.OTHER, LetterBuckets.keyFor(""))
        assertEquals(LetterBuckets.OTHER, LetterBuckets.keyFor("   "))
        assertEquals(LetterBuckets.OTHER, LetterBuckets.keyFor("Øyvind"))
        assertEquals(LetterBuckets.OTHER, LetterBuckets.keyFor("坂本龍一"))
    }

    @Test
    fun `order is a to z then digits then other`() {
        val expected = ('A'..'Z').map(Char::toString) + "0-9" + "#"
        assertEquals(expected, LetterBuckets.ORDER)
        assertEquals("0-9", LetterBuckets.DIGITS)
        assertEquals("#", LetterBuckets.OTHER)
    }

    @Test
    fun `group keeps only non empty buckets in order with members alphabetical`() {
        val names = listOf("Slowdive", "2Pac", "The Beatles", "Bauhaus", "Édith Piaf", "blur", "…Trail", "Swans")
        val grouped = LetterBuckets.group(names) { it }

        assertEquals(listOf("B", "E", "S", "0-9", "#"), grouped.keys.toList())
        assertEquals(listOf("Bauhaus", "The Beatles", "blur"), grouped.getValue("B"))
        assertEquals(listOf("Slowdive", "Swans"), grouped.getValue("S"))
        assertEquals(listOf("2Pac"), grouped.getValue("0-9"))
        assertEquals(listOf("…Trail"), grouped.getValue("#"))
    }

    @Test
    fun `group of nothing is empty`() {
        assertTrue(LetterBuckets.group(emptyList<String>()) { it }.isEmpty())
    }
}
