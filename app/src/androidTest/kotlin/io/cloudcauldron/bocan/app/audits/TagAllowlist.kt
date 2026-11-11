package io.cloudcauldron.bocan.app.audits

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.cloudcauldron.bocan.app.e2e.E2ETags

/**
 * The only place an actionable node may go untagged, one entry per reason. Every entry is a
 * node Material builds inside a component and gives the app no modifier for; a control the
 * app builds gets a tag instead of an entry here. Entries match by structure, never by a
 * label, so they hold in every language, and each one is anchored to a node the app did tag,
 * so a sheet that forgets its tagged handle still fails the audit.
 */
enum class TagAllowlist(private val reason: String, private val matcher: (SemanticsNode) -> Boolean) {
    /**
     * `ModalBottomSheet` wraps its drag handle in two nodes that carry the handle's tap
     * (expand, collapse) and long press; the `dragHandle` slot only reaches the handle inside.
     * Allowed when the node is exactly the size of a descendant tagged [E2ETags.Common.SHEET_HANDLE].
     */
    SheetHandleWrapper("Material wraps the sheet's drag handle in its own clickable nodes", ::wrapsTaggedSheetHandle),

    /**
     * `ModalBottomSheet` draws a full-window scrim that dismisses the sheet when tapped
     * ("Close sheet" to TalkBack). Allowed when it fills a window that also holds a tagged
     * sheet handle, which is to say a bottom sheet the app built.
     */
    SheetScrim("Material's bottom sheet scrim, which closes the sheet", ::isScrimOfTaggedSheet);

    fun matches(node: SemanticsNode): Boolean = matcher(node)

    override fun toString(): String = "$name ($reason)"
}

private fun wrapsTaggedSheetHandle(node: SemanticsNode): Boolean =
    node.descendants().any { it.isTagged(E2ETags.Common.SHEET_HANDLE) && it.size == node.size }

private fun isScrimOfTaggedSheet(node: SemanticsNode): Boolean {
    val root = node.root()
    return node.size == root.size && root.descendants().any { it.isTagged(E2ETags.Common.SHEET_HANDLE) }
}

private fun SemanticsNode.isTagged(tag: String): Boolean = config.getOrNull(SemanticsProperties.TestTag) == tag

private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = children.asSequence().flatMap { sequenceOf(it) + it.descendants() }

private tailrec fun SemanticsNode.root(): SemanticsNode = parent?.root() ?: this
