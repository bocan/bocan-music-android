package io.cloudcauldron.bocan.playback

import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity

/**
 * The stable identity Media3 carries on every MediaItem, and the choke point for
 * parsing it back. Tracks are `track:<numericId>` and episodes are
 * `episode:<stringId>`. A clip track uses its own clip id, never the source
 * track's id (a clip shares the source file but is its own queue item).
 */
sealed interface MediaId {
    val raw: String

    data class Track(val trackId: Long) : MediaId {
        override val raw: String get() = "$TRACK_PREFIX$trackId"
    }

    data class Episode(val episodeId: String) : MediaId {
        override val raw: String get() = "$EPISODE_PREFIX$episodeId"
    }

    companion object {
        const val TRACK_PREFIX = "track:"
        const val EPISODE_PREFIX = "episode:"

        fun of(track: TrackEntity): Track = Track(track.id)

        fun of(episode: EpisodeEntity): Episode = Episode(episode.id)

        /** Parse a raw media id, or null if it is neither a track nor an episode id. */
        fun parse(raw: String): MediaId? = when {
            raw.startsWith(TRACK_PREFIX) ->
                raw.removePrefix(TRACK_PREFIX).toLongOrNull()?.let(::Track)
            raw.startsWith(EPISODE_PREFIX) ->
                raw.removePrefix(EPISODE_PREFIX).takeIf { it.isNotEmpty() }?.let(::Episode)
            else -> null
        }
    }
}
