package io.cloudcauldron.bocan.app.podcasts

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ShowNotesLinksTests {
    @Test
    fun `https links are secure and open without a prompt`() {
        assertTrue(ShowNotesLinks.isSecure("https://example.com/ep"))
        assertTrue(ShowNotesLinks.isSecure("HTTPS://Example.com"))
        assertTrue(ShowNotesLinks.isSecure("  https://example.com  "))
    }

    @Test
    fun `http and other schemes are insecure and must be confirmed`() {
        assertFalse(ShowNotesLinks.isSecure("http://example.com"))
        assertFalse(ShowNotesLinks.isSecure("ftp://example.com/file"))
        assertFalse(ShowNotesLinks.isSecure("mailto:someone@example.com"))
        assertFalse(ShowNotesLinks.isSecure(""))
    }
}
