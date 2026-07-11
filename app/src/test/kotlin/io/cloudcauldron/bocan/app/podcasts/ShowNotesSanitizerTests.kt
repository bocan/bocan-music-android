package io.cloudcauldron.bocan.app.podcasts

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ShowNotesSanitizerTests {
    @Test
    fun `script blocks are removed entirely`() {
        val out = ShowNotesSanitizer.sanitize("<p>Hi</p><script>alert('x')</script><p>Bye</p>")
        assertFalse(out.contains("script", ignoreCase = true))
        assertFalse(out.contains("alert"))
        assertTrue(out.contains("<p>Hi</p>"))
        assertTrue(out.contains("<p>Bye</p>"))
    }

    @Test
    fun `style blocks are removed`() {
        val out = ShowNotesSanitizer.sanitize("<style>.a{color:red}</style><p>Text</p>")
        assertFalse(out.contains("color:red"))
        assertTrue(out.contains("<p>Text</p>"))
    }

    @Test
    fun `http links are preserved`() {
        val out = ShowNotesSanitizer.sanitize("""<a href="https://example.com/ep">Listen</a>""")
        assertTrue(out.contains("""href="https://example.com/ep""""))
        assertTrue(out.contains("Listen"))
    }

    @Test
    fun `javascript urls are neutralised`() {
        val out = ShowNotesSanitizer.sanitize("""<a href="javascript:steal()">x</a>""")
        assertFalse(out.contains("javascript:", ignoreCase = true))
        assertTrue(out.contains("""href="#""""))
    }

    @Test
    fun `inline event handlers are stripped`() {
        val out = ShowNotesSanitizer.sanitize("""<p onclick="hack()">Text</p>""")
        assertFalse(out.contains("onclick", ignoreCase = true))
        assertTrue(out.contains("Text"))
    }

    @Test
    fun `html comments are removed`() {
        val out = ShowNotesSanitizer.sanitize("<!-- secret --><p>Visible</p>")
        assertFalse(out.contains("secret"))
        assertTrue(out.contains("Visible"))
    }
}
