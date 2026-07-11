package io.cloudcauldron.bocan.playback.podcast

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ChaptersParserTests {
    @Test
    fun `a full spec sample parses to milliseconds`() {
        val body = """
            {"version":"1.2.0","chapters":[
              {"startTime":0,"title":"Intro","endTime":90.5,"img":"http://x/a.jpg","url":"http://x/a"},
              {"startTime":90.5,"title":"Main"}
            ]}
        """.trimIndent()
        val chapters = ChaptersParser.parse(body)
        assertEquals(2, chapters.size)
        assertEquals(Chapter("Intro", 0, 90_500, "http://x/a.jpg", "http://x/a"), chapters[0])
        assertEquals("Main", chapters[1].title)
        assertEquals(90_500, chapters[1].startTimeMs)
    }

    @Test
    fun `out of order chapters are sorted by start`() {
        val body = """
            {"chapters":[{"startTime":120,"title":"B"},{"startTime":0,"title":"A"}]}
        """.trimIndent()
        assertEquals(listOf("A", "B"), ChaptersParser.parse(body).map { it.title })
    }

    @Test
    fun `entries without a start time are skipped`() {
        val body = """
            {"chapters":[{"title":"No start"},{"startTime":10,"title":"Good"}]}
        """.trimIndent()
        val chapters = ChaptersParser.parse(body)
        assertEquals(listOf("Good"), chapters.map { it.title })
    }

    @Test
    fun `malformed json yields an empty list`() {
        assertTrue(ChaptersParser.parse("not json at all {").isEmpty())
        assertTrue(ChaptersParser.parse("").isEmpty())
    }

    @Test
    fun `active chapter is start inclusive and end exclusive`() {
        val chapters = listOf(
            Chapter("A", 0, 100_000),
            Chapter("B", 100_000, 200_000),
            Chapter("C", 200_000)
        )
        assertEquals(0, ChaptersParser.activeChapterIndex(chapters, 0))
        assertEquals(0, ChaptersParser.activeChapterIndex(chapters, 99_999))
        assertEquals(1, ChaptersParser.activeChapterIndex(chapters, 100_000))
        assertEquals(2, ChaptersParser.activeChapterIndex(chapters, 500_000))
    }

    @Test
    fun `a gap before the first chapter has no active chapter`() {
        val chapters = listOf(Chapter("A", 10_000, 20_000))
        assertEquals(-1, ChaptersParser.activeChapterIndex(chapters, 5_000))
    }
}
