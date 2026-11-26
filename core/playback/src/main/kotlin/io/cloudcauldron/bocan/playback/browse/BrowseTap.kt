package io.cloudcauldron.bocan.playback.browse

import androidx.media3.common.MediaItem
import io.cloudcauldron.bocan.playback.MediaId

/**
 * The media id of a song row in the car: the song, and the list it was shown in.
 *
 * Android Auto sends back only the id of the row that was tapped, so the row must carry its
 * own context or a tap can queue nothing but that one song. [occurrence] tells two copies of
 * one song in a playlist apart (0 is the first copy). It counts copies of this song, never
 * rows, so the id stays valid when other songs move. The id never enters the queue: the
 * session expands it into plain [MediaId]s, so stats and scrobbles see normal tracks.
 */
data class BrowseTap(val parentId: String, val trackId: Long, val occurrence: Int = 0) {
    val raw: String get() = "$PREFIX$trackId/$occurrence/$parentId"

    val track: MediaId.Track get() = MediaId.Track(trackId)

    /**
     * Where this song starts in a resolved queue: its own copy, or its first copy when the
     * list has changed since the car read it. Null when the song is not in [items].
     */
    fun startIndexIn(items: List<MediaItem>): Int? {
        val copies = items.indices.filter { items[it].mediaId == track.raw }
        return copies.getOrNull(occurrence) ?: copies.firstOrNull()
    }

    companion object {
        private const val PREFIX = "bocan/tap/"
        private const val PARTS = 3

        /** Parse a raw media id, or null if it is not a song row with a parent list. */
        fun parse(raw: String): BrowseTap? {
            if (!raw.startsWith(PREFIX)) return null
            val parts = raw.removePrefix(PREFIX).split('/', limit = PARTS)
            val trackId = parts.getOrNull(0)?.toLongOrNull()
            val occurrence = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 }
            val parentId = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
            return if (trackId != null && occurrence != null && parentId != null) {
                BrowseTap(parentId, trackId, occurrence)
            } else {
                null
            }
        }
    }
}
