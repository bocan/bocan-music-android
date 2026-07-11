package io.cloudcauldron.bocan.playback.lyrics

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class LrcParserTests {
    private fun synced(raw: String): List<LyricLine> = assertIs<LyricsDoc.Synced>(LrcParser.parse(raw)).lines

    @Test
    fun `a single timed line parses to its millisecond offset`() {
        val lines = synced("[00:12.00]First line")
        assertEquals(listOf(LyricLine(12_000, "First line")), lines)
    }

    @Test
    fun `multiple timestamps on one line expand to multiple sorted entries`() {
        val lines = synced("[02:15.50][01:02.00]chorus")
        assertEquals(
            listOf(LyricLine(62_000, "chorus"), LyricLine(135_500, "chorus")),
            lines
        )
    }

    @Test
    fun `decisecond, centisecond, and millisecond fractions all resolve`() {
        assertEquals(1_500, synced("[00:01.5]a").single().timeMs)
        assertEquals(1_500, synced("[00:01.50]a").single().timeMs)
        assertEquals(1_500, synced("[00:01.500]a").single().timeMs)
    }

    @Test
    fun `a line with no fraction parses to whole seconds`() {
        assertEquals(75_000, synced("[01:15]a").single().timeMs)
    }

    @Test
    fun `positive offset shifts every line later`() {
        val lines = synced("[offset:+500]\n[00:10.00]a\n[00:20.00]b")
        assertEquals(listOf(10_500L, 20_500L), lines.map { it.timeMs })
    }

    @Test
    fun `negative offset shifts earlier and clamps at zero`() {
        val lines = synced("[offset:-2000]\n[00:01.00]a\n[00:10.00]b")
        assertEquals(listOf(0L, 8_000L), lines.map { it.timeMs })
    }

    @Test
    fun `garbage interleaved with valid lines is skipped and valid lines survive`() {
        val raw = """
            [00:01.00]a
            not a lyric line
            [00:02.00]b
            [malformed
            [00:03.00]c
        """.trimIndent()
        val lines = synced(raw)
        assertEquals(listOf("a", "b", "c"), lines.map { it.text })
        assertEquals(3, lines.size)
    }

    @Test
    fun `metadata tags are not treated as timestamps`() {
        val lines = synced("[ar:Runrig]\n[ti:Song]\n[00:05.00]words")
        assertEquals(listOf(LyricLine(5_000, "words")), lines)
    }

    @Test
    fun `wholly untimed text becomes unsynced`() {
        val doc = LrcParser.parse("just some\nplain lyrics")
        val unsynced = assertIs<LyricsDoc.Unsynced>(doc)
        assertEquals("just some\nplain lyrics", unsynced.text)
    }

    @Test
    fun `an empty timestamped line keeps an empty text entry`() {
        assertEquals(listOf(LyricLine(30_000, "")), synced("[00:30.00]"))
    }

    @Test
    fun `the golden fixture parses to the expected timed lines`() {
        val raw = LrcParserTests::class.java.getResourceAsStream("/fixtures/sample.lrc")!!
            .bufferedReader().readText()
        val lines = synced(raw)
        // offset +250 applied; the third line has two timestamps; garbage lines dropped.
        assertEquals(
            listOf(10_250L, 13_750L, 17_250L, 45_250L, 60_250L),
            lines.map { it.timeMs }
        )
        assertEquals("where the sun shines bright", lines[2].text)
        assertTrue(lines.all { it.text.isNotEmpty() })
    }
}
