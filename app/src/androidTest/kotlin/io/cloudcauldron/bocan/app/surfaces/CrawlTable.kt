package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.playback.queue.PlayerUiState

/**
 * One row of a crawl table (phase 17, slice 3): act on the control tagged [tag] (the one
 * labelled [row] when the tag repeats), then wait until [expect] holds. The table, not the
 * code that runs it, is the reviewable artifact; every row states its postcondition, so
 * "did not crash" can never be the check.
 */
data class Step(
    val tag: String,
    val action: Action,
    val expect: Postcondition,
    val row: String? = null,
    /** How long [expect] may take; longer only where the app itself waits (sync's discovery window). */
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    override fun toString(): String = "${action.describe()} '$tag'" + (row?.let { " ('$it')" } ?: "")

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}

/** What a step does to its control. */
sealed interface Action {
    fun describe(): String

    data object Tap : Action {
        override fun describe() = "tap"
    }

    data object LongPress : Action {
        override fun describe() = "long-press"
    }

    data class Type(val text: String) : Action {
        override fun describe() = "type '$text' into"
    }

    /**
     * Type text the table cannot know when it is built, read when the step runs: the pairing
     * code the fake Mac shows, which depends on nonces drawn during the crawl. [what] names it.
     */
    class TypeFrom(private val what: String, val text: () -> String) : Action {
        override fun describe() = "type $what into"
    }

    /** Empty a text field. */
    data object Clear : Action {
        override fun describe() = "clear"
    }

    /** The keyboard's action key (Search, Done) on a text field. */
    data object Submit : Action {
        override fun describe() = "submit"
    }

    /** Set a slider to [fraction] of its range through its set-progress action, as TalkBack does. */
    data class Slide(val fraction: Float) : Action {
        override fun describe() = "slide to $fraction of"
    }

    data class Swipe(val direction: Direction) : Action {
        override fun describe() = "swipe ${direction.name.lowercase()} on"
    }

    /** Long-press, then drag [rows] row heights down (negative is up): the queue's reorder. */
    data class Reorder(val rows: Int) : Action {
        override fun describe() = "drag $rows row(s) with"
    }

    /** Tap the link reading [text] inside the show notes (a TextView), for [CrawlTags.TEXT_LINK]. */
    data class TapLink(val text: String) : Action {
        override fun describe() = "tap the link '$text' in"
    }

    /** The system back gesture, for [CrawlTags.SYSTEM_BACK]: closes a sheet, menu, or dialog. */
    data object Back : Action {
        override fun describe() = "press back on"
    }

    /**
     * Touch nothing; only wait for the postcondition. For a screen already showing state the
     * app restored (after a process kill): the check is the point, there is nothing to do first.
     */
    data object Look : Action {
        override fun describe() = "look at"
    }
}

enum class Direction { Left, Right, Up, Down }

/** What must be true after a step. The interpreter waits for it, then fails naming what it found instead. */
sealed interface Postcondition {
    fun describe(): String

    data class Visible(val tag: String, val row: String? = null) : Postcondition {
        override fun describe() = "'$tag'" + (row?.let { " ('$it')" } ?: "") + " on screen"
    }

    /** The control is gone: a sheet or dialog dismissed, a row removed. */
    data class Gone(val tag: String, val row: String? = null) : Postcondition {
        override fun describe() = "'$tag'" + (row?.let { " ('$it')" } ?: "") + " gone"
    }

    /** A screen reached through navigation, recognised by a control only it has. */
    data class Navigated(val screen: String, val markerTag: String) : Postcondition {
        override fun describe() = "the $screen screen (marker '$markerTag')"
    }

    /** Text somewhere on screen, for a screen that has no tagged control in that state. */
    data class Shows(val text: String) : Postcondition {
        override fun describe() = "the text '$text' on screen"
    }

    data class Text(val tag: String, val contains: String) : Postcondition {
        override fun describe() = "'$tag' reading '$contains'"
    }

    /** A text field holding no text: cleared for a fresh attempt. */
    data class Empty(val tag: String) : Postcondition {
        override fun describe() = "'$tag' empty"
    }

    data class Toggled(val tag: String, val on: Boolean) : Postcondition {
        override fun describe() = "'$tag' switched ${if (on) "on" else "off"}"
    }

    /** The step's own toggle now reads the opposite of what it read before the step. */
    data object Flipped : Postcondition {
        override fun describe() = "the toggle flipped"
    }

    data class Selected(val tag: String, val row: String? = null) : Postcondition {
        override fun describe() = "'$tag'" + (row?.let { " ('$it')" } ?: "") + " selected"
    }

    /** The transport's state. Null fields are not checked; [check] covers anything else, named by [what]. */
    data class PlayerState(
        val playing: Boolean? = null,
        val mediaIdPrefix: String? = null,
        val what: String = "",
        val check: ((PlayerUiState) -> Boolean)? = null
    ) : Postcondition {
        override fun describe() = listOfNotNull(
            playing?.let { if (it) "playing" else "paused" },
            mediaIdPrefix?.let { "current item '$it...'" },
            what.ifBlank { null }
        ).joinToString(", ", prefix = "player ")
    }

    /** App state the screen does not show: a stored setting, a database row. [what] names it for the failure. */
    data class Holds(val what: String, val check: () -> Boolean) : Postcondition {
        override fun describe() = what
    }

    /** An intent left the app (stubbed, so nothing opens): a browser link. */
    data class IntentSent(val action: String, val uri: String) : Postcondition {
        override fun describe() = "an intent $action for $uri"
    }
}

/** Steps that are not about a tagged control. */
object CrawlTags {
    const val SYSTEM_BACK = "crawl.systemBack"
    const val TEXT_LINK = "crawl.textLink"

    val ALL = setOf(SYSTEM_BACK, TEXT_LINK)
}
