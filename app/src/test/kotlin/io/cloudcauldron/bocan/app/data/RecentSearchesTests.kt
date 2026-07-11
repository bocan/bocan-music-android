package io.cloudcauldron.bocan.app.data

import kotlin.test.assertEquals
import org.junit.Test

class RecentSearchesTests {
    @Test
    fun `a new query goes to the front`() {
        assertEquals(listOf("b", "a"), RecentSearches.updated(listOf("a"), "b"))
    }

    @Test
    fun `a repeated query moves to the front without duplicating`() {
        assertEquals(listOf("a", "c", "b"), RecentSearches.updated(listOf("c", "b", "a"), "a"))
    }

    @Test
    fun `matching is case insensitive and the query is trimmed`() {
        assertEquals(listOf("Rush"), RecentSearches.updated(listOf("rush"), "  Rush  "))
    }

    @Test
    fun `blank queries are ignored`() {
        assertEquals(listOf("a"), RecentSearches.updated(listOf("a"), "   "))
    }

    @Test
    fun `the list is capped at the maximum`() {
        val many = (1..RecentSearches.MAX).map { "q$it" }
        val updated = RecentSearches.updated(many, "new")
        assertEquals(RecentSearches.MAX, updated.size)
        assertEquals("new", updated.first())
        assertEquals("q${RecentSearches.MAX - 1}", updated.last())
    }
}
