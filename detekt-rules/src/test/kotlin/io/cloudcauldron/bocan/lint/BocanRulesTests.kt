package io.cloudcauldron.bocan.lint

import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.assertEquals
import org.junit.Test

class BocanRulesTests {
    @Test
    fun `concatenating a string resource with plus is flagged`() {
        val code = """
            fun greeting(): String = stringResource(R.string.hello) + " world"
        """.trimIndent()

        assertEquals(1, UserVisibleStringConcatenation().lint(code).size)
    }

    @Test
    fun `a resource call nested in an operand is still flagged`() {
        val code = """
            fun caption(loved: Boolean): String =
                "start" + (if (loved) stringResource(R.string.loved) else "")
        """.trimIndent()

        assertEquals(1, UserVisibleStringConcatenation().lint(code).size)
    }

    @Test
    fun `plain arithmetic and non-resource strings pass`() {
        val code = """
            fun math(a: Int, b: Int): Int = a + b
            fun key(prefix: String): String = prefix + "_suffix"
        """.trimIndent()

        assertEquals(0, UserVisibleStringConcatenation().lint(code).size)
    }

    @Test
    fun `an inline literal in Text is flagged`() {
        val code = """
            fun render() {
                Text("Hello there")
                Text(text = "General ${'$'}rank")
            }
        """.trimIndent()

        assertEquals(2, BareTextLiteral().lint(code).size)
    }

    @Test
    fun `Text with a string resource passes`() {
        val code = """
            fun render() {
                Text(stringResource(R.string.hello))
                Text(text = title, maxLines = 1)
            }
        """.trimIndent()

        assertEquals(0, BareTextLiteral().lint(code).size)
    }
}
