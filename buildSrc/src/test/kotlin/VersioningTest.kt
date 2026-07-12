import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VersioningTest {
    @Test
    fun `derives the documented code for known versions`() {
        assertEquals(100, versionCodeOf("0.1.0"))
        assertEquals(10203, versionCodeOf("1.2.3"))
        assertEquals(20000, versionCodeOf("2.0.0"))
        assertEquals(19999, versionCodeOf("1.99.99"))
    }

    @Test
    fun `a major bump always outranks the highest hotfix of the previous major`() {
        // The gotcha the phase calls out: 2.0.0 must never collide with a 1.99.x scheme.
        assertTrue(versionCodeOf("2.0.0") > versionCodeOf("1.99.99"))
    }

    @Test
    fun `codes increase monotonically along an ascending release timeline`() {
        val timeline = listOf(
            "0.1.0", "0.1.1", "0.2.0", "0.9.99", "1.0.0",
            "1.0.1", "1.10.0", "1.99.99", "2.0.0", "2.0.1",
        )
        val codes = timeline.map(::versionCodeOf)
        assertEquals(codes.sorted(), codes)
        assertEquals(codes.distinct(), codes)
    }

    @Test
    fun `ignores pre-release and build suffixes`() {
        assertEquals(versionCodeOf("1.2.3"), versionCodeOf("1.2.3-rc1"))
        assertEquals(versionCodeOf("1.2.3"), versionCodeOf("1.2.3+build.7"))
    }

    @Test
    fun `rejects a minor or patch that would break monotonicity`() {
        assertFailsWith<IllegalArgumentException> { versionCodeOf("1.100.0") }
        assertFailsWith<IllegalArgumentException> { versionCodeOf("1.0.100") }
    }

    @Test
    fun `rejects malformed version names`() {
        assertFailsWith<IllegalArgumentException> { versionCodeOf("1.2") }
        assertFailsWith<IllegalArgumentException> { versionCodeOf("1.2.3.4") }
        assertFailsWith<IllegalArgumentException> { versionCodeOf("1.x.0") }
    }
}
