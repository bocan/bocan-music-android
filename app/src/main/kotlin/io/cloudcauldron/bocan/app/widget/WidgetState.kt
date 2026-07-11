package io.cloudcauldron.bocan.app.widget

import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import kotlinx.serialization.Serializable

/**
 * The single snapshot the Glance widget renders from. Parcel and JSON friendly, so
 * recomposition is deterministic and the widget can render from persisted state on a
 * cold process without the app running (phase 10 gotcha). Derived from [PlayerUiState]
 * by [fromPlayer], which is pure and unit tested.
 */
@Serializable
data class WidgetState(
    val hasContent: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val isPodcast: Boolean = false,
    val artworkUri: String? = null
) {
    companion object {
        /** The empty (nothing playing) state. */
        val EMPTY = WidgetState()

        /** Map the live transport snapshot to a widget snapshot. */
        fun fromPlayer(ui: PlayerUiState): WidgetState {
            val current = ui.current ?: return EMPTY
            return WidgetState(
                hasContent = true,
                title = current.title,
                subtitle = current.artist.orEmpty(),
                isPlaying = ui.isPlaying,
                isPodcast = current.mediaId.startsWith(EPISODE_PREFIX),
                artworkUri = current.artworkUri
            )
        }

        private const val EPISODE_PREFIX = "episode:"
    }
}
