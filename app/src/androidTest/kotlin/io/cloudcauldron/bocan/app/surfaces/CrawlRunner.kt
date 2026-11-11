package io.cloudcauldron.bocan.app.surfaces

import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.toSize
import androidx.test.espresso.Espresso
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import io.cloudcauldron.bocan.app.Ui
import io.cloudcauldron.bocan.app.audits.TagAudit
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import org.hamcrest.Matcher
import org.hamcrest.Matchers.containsString

/**
 * Runs crawl tables. Before each step it audits the screen (every actionable node must carry a
 * tag, see [TagAudit]) and records the tags it saw; then it performs the step's action and waits
 * for the postcondition. A postcondition that never holds fails with the crawl, the step number,
 * the action and tag, what was expected, and what was found instead.
 */
class CrawlRunner(
    private val rule: ComposeTestRule,
    private val ui: Ui,
    private val audit: TagAudit,
    private val player: () -> PlayerUiState,
    private val screenshot: (String) -> Unit
) {
    fun run(crawl: String, table: List<Step>) {
        table.forEachIndexed { index, step ->
            try {
                audit.check("$crawl, before step ${index + 1} ($step)")
                val toggleBefore = if (step.expect == Postcondition.Flipped) toggleState(step) else null
                act(step)
                verify(crawl, index, step, toggleBefore)
            } catch (failure: AssertionError) {
                // A picture of the screen at the failing step, for a person; the assertion still decides.
                // Taking it must never hide the failure (no Test Storage service under a bare am instrument).
                try {
                    screenshot("${crawl.replace(' ', '-')}-step-${index + 1}-failed")
                } catch (noPicture: Exception) {
                    failure.addSuppressed(noPicture)
                }
                throw failure
            }
        }
        audit.check("$crawl, after the last step")
    }

    private fun act(step: Step) {
        when (val action = step.action) {
            Action.Look -> Unit
            Action.Back -> {
                rule.waitForIdle()
                Espresso.pressBack()
            }
            is Action.TapLink -> {
                rule.waitForIdle()
                Espresso.onView(withText(containsString(action.text))).perform(ClickSpan(action.text))
            }
            // Touch needs the control on screen; the rest go through semantics actions, as TalkBack
            // would, and never scroll. Scrolling a rotated band slider into view in its horizontal
            // row left the app never idle, so a step that does not touch must not scroll.
            Action.Tap, Action.LongPress, is Action.Swipe, is Action.Reorder -> perform(onScreen(control(step)), action)
            else -> perform(control(step), action)
        }
    }

    private fun perform(node: SemanticsNodeInteraction, action: Action) {
        when (action) {
            Action.Tap -> node.performClick()
            Action.LongPress -> node.performTouchInput { longClick() }
            is Action.Type -> node.performTextInput(action.text)
            is Action.TypeFrom -> node.performTextInput(action.text())
            Action.Clear -> node.performTextClearance()
            Action.Submit -> node.performImeAction()
            is Action.Slide -> slide(node, action.fraction)
            is Action.Swipe -> node.performTouchInput {
                when (action.direction) {
                    Direction.Left -> swipeLeft()
                    Direction.Right -> swipeRight()
                    Direction.Up -> swipeUp()
                    Direction.Down -> swipeDown()
                }
            }
            is Action.Reorder -> node.performTouchInput {
                down(center)
                advanceEventTime(LONG_PRESS_MS)
                moveBy(androidx.compose.ui.geometry.Offset(0f, height * ROW_HEIGHTS_PER_HANDLE * action.rows))
                up()
            }
            Action.Back, Action.Look, is Action.TapLink -> error("handled in act")
        }
    }

    private fun slide(node: SemanticsNodeInteraction, fraction: Float) {
        val range = node.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range
        val target = range.start + fraction * (range.endInclusive - range.start)
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(target) }
    }

    /** The step's control: by tag alone (exactly one) or, for a repeated tag, by its row label. */
    private fun control(step: Step): SemanticsNodeInteraction =
        if (step.row == null) ui.awaitTag(step.tag) else ui.awaitRow(step.tag, step.row)

    /**
     * Scroll a control fully into view when it is off screen or only partly on it. A touch lands
     * on the control's centre, so a control clipped by its scrolling parent (a button half under
     * a top bar, which is common in landscape) would take the tap somewhere else. A control with
     * no scrolling parent (in a sheet, say) is touched where it is.
     *
     * `performScrollTo` moves only the nearest scrolling parent, so a control in a sideways row
     * inside a scrolling page (the equalizer's presets) would come into its row's view and stay
     * below the page's fold. Each scrolling ancestor is therefore asked in turn, innermost first,
     * to bring the next one in.
     */
    private fun onScreen(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        scrollIntoView(node.fetchSemanticsNode())
        return node
    }

    private fun scrollIntoView(node: SemanticsNode) {
        if (!clipped(node)) return
        var current: SemanticsNode? = node
        while (current != null) {
            try {
                rule.onNode(withId(current.id), useUnmergedTree = true).performScrollTo()
            } catch (noScrollParent: AssertionError) {
                // Nothing scrolls it: touch it where it is, and let the postcondition judge.
                break
            }
            current = generateSequence(current.parent) { it.parent }.firstOrNull { SemanticsActions.ScrollBy in it.config }
        }
    }

    private fun clipped(node: SemanticsNode): Boolean {
        val whole = Rect(node.positionInWindow, node.size.toSize())
        val shown = node.boundsInWindow
        return shown.width < whole.width - CLIP_TOLERANCE_PX || shown.height < whole.height - CLIP_TOLERANCE_PX
    }

    private fun withId(id: Int) = SemanticsMatcher("semantics node $id") { it.id == id }

    private fun verify(crawl: String, index: Int, step: Step, toggleBefore: ToggleableState?) {
        var found = "nothing checked yet"
        try {
            rule.waitUntil(step.timeoutMs) {
                val (holds, seen) = evaluate(step, toggleBefore)
                found = seen
                holds
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError(
                "crawl '$crawl', step ${index + 1}: $step expected ${step.expect.describe()} within ${step.timeoutMs} ms, found $found",
                timeout
            )
        }
    }

    private fun evaluate(step: Step, toggleBefore: ToggleableState?): Pair<Boolean, String> = when (val expect = step.expect) {
        // A row may sit past the fold of its list (search results in landscape, a chip at the end of
        // a sideways row): scroll to it as a person would, composing it first if a lazy list has
        // not. A control without a row label must be on screen as it is.
        is Postcondition.Visible -> nodes(expect.tag, expect.row).let { found ->
            displayed(found).also { (shown, _) ->
                if (!shown && expect.row != null) {
                    if (found.isEmpty()) ui.reveal(Ui.rowMatchers(expect.tag, expect.row).first()) else scrollIntoView(found.first())
                }
            }
        }
        is Postcondition.Navigated -> displayed(nodes(expect.markerTag, null))
        is Postcondition.Gone -> nodes(expect.tag, expect.row).let { it.isEmpty() to "${it.size} node(s) still there" }
        is Postcondition.Shows -> rule.onAllNodes(hasText(expect.text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().let { it.isNotEmpty() to "${it.size} node(s) with that text" }
        is Postcondition.Text -> nodes(expect.tag, null).map(::textOf).let { texts ->
            texts.any { it.contains(expect.contains) } to "texts $texts"
        }
        is Postcondition.Empty -> nodes(expect.tag, null).map { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
            .let { texts -> (texts.isNotEmpty() && texts.all { it.isNullOrEmpty() }) to "texts $texts" }
        is Postcondition.Toggled -> nodes(expect.tag, null).map { toggle(it) }.let { states ->
            states.contains(if (expect.on) ToggleableState.On else ToggleableState.Off) to "states $states"
        }
        Postcondition.Flipped -> toggleState(step).let { now ->
            (now != null && now != toggleBefore) to "state $now, before the step $toggleBefore"
        }
        is Postcondition.Selected -> nodes(expect.tag, expect.row).map { it.config.getOrNull(SemanticsProperties.Selected) }
            .let { it.contains(true) to "selected flags $it" }
        is Postcondition.PlayerState -> player().let { state ->
            val holds = (expect.playing == null || state.isPlaying == expect.playing) &&
                (expect.mediaIdPrefix == null || state.current?.mediaId?.startsWith(expect.mediaIdPrefix) == true) &&
                (expect.check == null || expect.check.invoke(state))
            holds to "player $state"
        }
        is Postcondition.Holds -> expect.check() to "it did not hold"
        is Postcondition.IntentSent -> Intents.getIntents().map { "${it.action} ${it.dataString}" }.let { sent ->
            sent.any { it == "${expect.action} ${expect.uri}" } to "intents $sent"
        }
    }

    private fun nodes(tag: String, row: String?): List<SemanticsNode> {
        if (row == null) {
            return rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
        }
        Ui.rowMatchers(tag, row).forEach { matcher ->
            val found = rule.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false)
            if (found.isNotEmpty()) return found
        }
        return Ui.unmergedRowMatchers(tag, row).firstNotNullOfOrNull { matcher ->
            rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false).ifEmpty { null }
        }.orEmpty()
    }

    private fun displayed(found: List<SemanticsNode>): Pair<Boolean, String> {
        val shown = found.filter { it.boundsInWindow.width > 0f && it.boundsInWindow.height > 0f }
        return shown.isNotEmpty() to "${found.size} node(s), ${shown.size} with a size on screen"
    }

    private fun toggleState(step: Step): ToggleableState? = nodes(step.tag, step.row).firstNotNullOfOrNull { toggle(it) }

    private fun toggle(node: SemanticsNode): ToggleableState? = node.config.getOrNull(SemanticsProperties.ToggleableState)

    private fun textOf(node: SemanticsNode): String {
        val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        val editable = node.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        return (texts + editable + descriptions).joinToString(" ")
    }

    /** Clicks the ClickableSpan covering [text] in a TextView, the way a finger on the link would. */
    private class ClickSpan(private val text: String) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(TextView::class.java)

        override fun getDescription() = "click the link '$text'"

        override fun perform(uiController: UiController, view: View) {
            val textView = view as TextView
            val spanned = textView.text as Spanned
            val start = spanned.toString().indexOf(text)
            check(start >= 0) { "no '$text' in ${spanned.length} characters of text" }
            val span = spanned.getSpans(start, start + text.length, ClickableSpan::class.java).firstOrNull()
                ?: error("'$text' is not a link")
            span.onClick(textView)
            uiController.loopMainThreadUntilIdle()
        }
    }

    companion object {
        private const val LONG_PRESS_MS = 800L
        private const val CLIP_TOLERANCE_PX = 1f

        // The queue's drag handle is a 48 dp target in a 64 dp row.
        private const val ROW_HEIGHTS_PER_HANDLE = 64f / 48f
    }
}
