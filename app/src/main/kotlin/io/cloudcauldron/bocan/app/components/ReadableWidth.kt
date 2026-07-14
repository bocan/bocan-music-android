package io.cloudcauldron.bocan.app.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// A comfortable single-pane reading width. Wider than any phone in portrait, so the cap only
// bites on landscape and tablet widths; portrait keeps filling the screen as before.
private val READABLE_MAX_WIDTH = 720.dp

/**
 * Caps its content to a comfortable reading width and centres it, so lists and settings do not
 * stretch edge to edge on wide (landscape, tablet) screens where a row's title sits far from its
 * trailing value. The content lambda receives the modifier to apply to its root: it fills the
 * capped width and the full height. In portrait the cap is wider than the screen, so this is a
 * no-op and the content fills the width exactly as it did before.
 */
@Composable
fun ReadableWidth(modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        content(Modifier.widthIn(max = READABLE_MAX_WIDTH).fillMaxSize())
    }
}
