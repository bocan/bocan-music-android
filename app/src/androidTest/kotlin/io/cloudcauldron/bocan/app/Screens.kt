package io.cloudcauldron.bocan.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.cloudcauldron.bocan.app.e2e.E2ETags

/**
 * Finders shared by the page objects. Controls are found by their stable [E2ETags]; rows
 * by the start of their merged content description (the TalkBack sentence, which always
 * opens with the row's title and a comma), never by position.
 */
class Ui(private val rule: ComposeTestRule) {
    /**
     * Tags are looked up in the unmerged tree: a tagged child of a clickable parent (the mini
     * player's cover inside its bar) is merged away in the default tree and loses its tag there.
     */
    fun awaitTag(tag: String, timeoutMs: Long = TIMEOUT_MS): SemanticsNodeInteraction {
        await("control '$tag'", timeoutMs) {
            rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
        return rule.onNode(hasTestTag(tag), useUnmergedTree = true)
    }

    fun tap(tag: String) {
        awaitTag(tag).performClick()
    }

    /** Wait until at least one node carries [tag]: a repeated control such as a row. */
    fun awaitAny(tag: String, timeoutMs: Long = TIMEOUT_MS) {
        await("any control '$tag'", timeoutMs) {
            rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun awaitRow(title: String, timeoutMs: Long = TIMEOUT_MS): SemanticsNodeInteraction {
        val matcher = rowTitled(title)
        await("row '$title'", timeoutMs) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        return rule.onAllNodes(matcher)[0]
    }

    /**
     * A repeated control: the node tagged [tag] whose TalkBack sentence starts with "[label], ",
     * or, for rows without one (list items, chips, menu options), whose own text is [label].
     */
    fun awaitRow(tag: String, label: String, timeoutMs: Long = TIMEOUT_MS): SemanticsNodeInteraction {
        val matcher = hasTestTag(tag) and (rowTitled(label) or hasText(label))
        await("'$label' tagged '$tag'", timeoutMs) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        return rule.onAllNodes(matcher)[0]
    }

    /**
     * Scroll the lazy container tagged [containerTag] until the row titled [title] is composed,
     * then return it. The row may not be in the list yet (a seed still landing), so the scroll
     * is retried until it finds the row or the timeout passes.
     */
    fun scrollToRow(containerTag: String, title: String, timeoutMs: Long = TIMEOUT_MS): SemanticsNodeInteraction {
        val container = awaitTag(containerTag, timeoutMs)
        var lastMiss: AssertionError? = null
        try {
            rule.waitUntil(timeoutMs) {
                try {
                    container.performScrollToNode(rowTitled(title))
                    true
                } catch (notYet: AssertionError) {
                    // performScrollToNode asserts when no child matches yet: keep it for the report and retry.
                    lastMiss = notYet
                    false
                }
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("never found row '$title' in '$containerTag' within $timeoutMs ms: ${lastMiss?.message}", timeout)
        }
        return awaitRow(title)
    }

    private fun await(what: String, timeoutMs: Long, condition: () -> Boolean) {
        try {
            rule.waitUntil(timeoutMs, condition)
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("never found $what within $timeoutMs ms", timeout)
        }
    }

    companion object {
        const val TIMEOUT_MS = 10_000L

        /** A row whose TalkBack sentence starts with "[title], ". */
        fun rowTitled(title: String) = SemanticsMatcher("row titled '$title'") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith("$title, ") }
        }
    }
}

/** First run: the welcome flow a fresh unpaired install lands on. */
class OnboardingScreen(private val ui: Ui) {
    fun skip() = ui.tap(E2ETags.Onboarding.SKIP)
}

/** The library root: six tabs over the synced library. */
class LibraryScreen(private val ui: Ui) {
    fun open() = ui.tap(E2ETags.Nav.LIBRARY)

    fun albumsTab() = ui.tap(E2ETags.Library.TAB_ALBUMS)

    fun openAlbum(name: String) {
        ui.scrollToRow(E2ETags.Library.ALBUMS_GRID, name).performClick()
    }
}

/** An album's track list. */
class AlbumScreen(private val ui: Ui) {
    fun playTrack(title: String) {
        ui.awaitRow(title).performClick()
    }
}

/** The bar above the navigation while something is loaded. */
class MiniPlayer(private val ui: Ui) {
    fun awaitArtwork() = ui.awaitTag(E2ETags.MiniPlayer.ARTWORK)

    fun playPause() = ui.tap(E2ETags.MiniPlayer.PLAY_PAUSE)

    fun open() = ui.tap(E2ETags.MiniPlayer.OPEN)
}

/** The full player. */
class NowPlayingScreen(private val ui: Ui) {
    fun playPause() = ui.tap(E2ETags.NowPlaying.PLAY_PAUSE)

    fun next() = ui.tap(E2ETags.NowPlaying.NEXT)

    fun previous() = ui.tap(E2ETags.NowPlaying.PREVIOUS)
}

/** The podcasts root: continue listening and the shows grid. */
class PodcastsScreen(private val ui: Ui) {
    fun open() = ui.tap(E2ETags.Nav.PODCASTS)

    fun openShow(title: String) {
        ui.scrollToRow(E2ETags.Podcasts.SHOWS_GRID, title).performClick()
    }
}

/** One show's episode list. */
class ShowScreen(private val ui: Ui) {
    fun playEpisode(title: String) {
        ui.awaitRow(title).performClick()
    }
}
