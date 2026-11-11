package io.cloudcauldron.bocan.app.audits

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * The "every control has a tag" check (phase 17). At each screen or sheet a crawl reaches,
 * [check] collects every node in the unmerged semantics tree, across every window (the
 * activity, dialogs, menus, and sheets), that a user can act on: click, long-click, toggle,
 * drag a value, or type. A node without a test tag is a gap unless [TagAllowlist] explains it.
 * Gaps are gathered over the whole crawl and reported together by [assertNoGaps], so one run
 * lists every untagged control rather than only the first.
 */
class TagAudit(private val rule: ComposeTestRule) {
    private val gaps = linkedSetOf<String>()
    private val screens = mutableListOf<String>()

    /** The tags seen on actionable nodes so far; slice 3's coverage check compares them with the crawl tables. */
    val seenTags = sortedSetOf<String>()

    /** The screens [check] has run on, in order. */
    val checkedScreens: List<String> get() = screens

    fun check(screen: String) {
        rule.waitForIdle()
        screens += screen
        rule.onAllNodes(ACTIONABLE, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = true).forEach { node ->
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            when {
                tag != null -> seenTags += tag
                TagAllowlist.entries.none { it.matches(node) } -> gaps += "$screen: ${describe(node)}"
            }
        }
    }

    fun assertNoGaps() {
        if (gaps.isEmpty()) return
        throw AssertionError(
            "${gaps.size} actionable node(s) have no E2ETags tag. Tag each control (app/src/main/kotlin/.../e2e/E2ETags.kt), " +
                "or, for a node the app cannot tag, add a commented entry to TagAllowlist. Screens checked: $screens\n" +
                gaps.joinToString("\n") { "  - $it" }
        )
    }

    companion object {
        /** Click, long-click, toggle (a toggle is a click with a state), set-progress (sliders), and text input. */
        val ACTIONABLE = SemanticsMatcher("is actionable") { node ->
            ACTIONS.any { node.config.contains(it) }
        }

        private val ACTIONS = listOf(
            SemanticsActions.OnClick,
            SemanticsActions.OnLongClick,
            SemanticsActions.SetProgress,
            SemanticsActions.SetText
        )

        /** A one-line description of an untagged node: role, content description, and the text in it or under it. */
        fun describe(node: SemanticsNode): String {
            val role = node.config.getOrNull(SemanticsProperties.Role)?.toString() ?: "no role"
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            val text = textUnder(node).take(MAX_TEXTS)
            val actions = ACTIONS.filter { node.config.contains(it) }.joinToString("/") { it.name }
            return "[$actions] $role, description=$description, text=$text, bounds=${node.boundsInRoot}"
        }

        private fun textUnder(node: SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap { textUnder(it) }

        private const val MAX_TEXTS = 3
    }
}
