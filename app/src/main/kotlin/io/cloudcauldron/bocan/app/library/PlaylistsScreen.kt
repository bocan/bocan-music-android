package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import io.cloudcauldron.bocan.app.components.ArtworkImage
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.model.PlaylistKind

/**
 * The playlist tree, folders honoured via parentId and rendered with indentation.
 * Folder rows are labels; manual and smart playlists open their detail. Read-only:
 * there are no reorder handles, since the Mac owns playlist order.
 */
@Composable
fun PlaylistsScreen(playlists: List<PlaylistEntity>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val depths = remember(playlists) { computeDepths(playlists) }
    LazyColumn(modifier = modifier) {
        items(playlists, key = { it.id }, contentType = { it.kind }) { playlist ->
            val indent = ((depths[playlist.id] ?: 0) * INDENT_DP).dp
            if (playlist.kind == PlaylistKind.Folder) {
                ListItem(
                    headlineContent = { Text(playlist.name, style = MaterialTheme.typography.titleSmall) },
                    leadingContent = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(start = indent)
                )
            } else {
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    leadingContent = { PlaylistLeading(playlist) },
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(playlist.id) }.padding(start = indent)
                )
            }
        }
    }
}

@Composable
private fun PlaylistLeading(playlist: PlaylistEntity) {
    // A synced accent can be any hex; fall back to primary when it would sit
    // below the 3:1 non-text contrast floor against the list surface.
    val accent = parseAccent(playlist.accentColor)
        ?.takeIf { contrastRatio(it, MaterialTheme.colorScheme.surface) >= MIN_ICON_CONTRAST }
    when {
        playlist.artworkHash != null -> ArtworkImage(
            artworkHash = playlist.artworkHash,
            modifier = Modifier.size(40.dp).clip(CircleShape)
        )
        accent != null -> Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(24.dp)
        )
        else -> Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

/** Depth from the root for indentation, following parentId. Cycles are guarded by a visit cap. */
private fun computeDepths(playlists: List<PlaylistEntity>): Map<Long, Int> {
    val byId = playlists.associateBy { it.id }
    return playlists.associate { playlist ->
        var depth = 0
        var parent = playlist.parentId
        var guard = 0
        while (parent != null && guard < byId.size) {
            depth++
            parent = byId[parent]?.parentId
            guard++
        }
        playlist.id to depth
    }
}

/** Parse a "#RRGGBB" accent to a Compose Color, or null when absent or malformed. */
private fun parseAccent(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        Color(hex.toColorInt())
    } catch (expected: IllegalArgumentException) {
        null
    }
}

private const val INDENT_DP = 16

/** WCAG contrast ratio between two opaque colors. */
private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
}

private const val MIN_ICON_CONTRAST = 3f
private const val LUMINANCE_OFFSET = 0.05f
