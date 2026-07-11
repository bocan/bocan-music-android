package io.cloudcauldron.bocan.playback.queue

/**
 * One immutable snapshot of the transport and queue for the UI. QueueController
 * exposes this as a StateFlow; phases 05 and 06 render it. Everything a Now Playing
 * bar, screen, or queue editor needs is here so those surfaces never touch the
 * player directly.
 */
data class PlayerUiState(
    val current: NowPlayingItem? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val queue: List<NowPlayingItem> = emptyList(),
    val queueIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffleActive: Boolean = false,
    val speed: Float = 1.0f
)

/** The display fields for one queue entry, resolved from its MediaItem metadata. */
data class NowPlayingItem(
    val mediaId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val durationMs: Long
)
