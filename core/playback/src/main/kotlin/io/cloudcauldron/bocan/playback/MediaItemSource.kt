package io.cloudcauldron.bocan.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.persistence.daos.PodcastDao
import kotlinx.coroutines.withContext

/**
 * Resolves stable [MediaId]s back to playable [MediaItem]s: the reverse of
 * [MediaItemFactory], used to build a queue from track ids and to rebuild the queue
 * from a persisted snapshot after process death.
 */
interface MediaItemSource {
    suspend fun resolve(ids: List<MediaId>): List<MediaItem>

    /** Convenience for the common all-tracks path (the library plays tracks by id). */
    suspend fun resolveTracks(trackIds: List<Long>): List<MediaItem> = resolve(trackIds.map(MediaId::Track))

    /** Convenience for the podcast path (plays episodes by string id). */
    suspend fun resolveEpisodes(episodeIds: List<String>): List<MediaItem> = resolve(episodeIds.map(MediaId::Episode))
}

/**
 * The database-backed [MediaItemSource]. Tracks and episodes are looked up in one query
 * each and mapped through [MediaItemFactory], preserving the caller's requested order.
 */
@UnstableApi
class DatabaseMediaItemSource(
    private val libraryDao: LibraryDao,
    private val podcastDao: PodcastDao,
    private val factory: MediaItemFactory,
    private val dispatchers: CoroutineDispatchers,
    private val log: AppLog
) : MediaItemSource {
    override suspend fun resolve(ids: List<MediaId>): List<MediaItem> = withContext(dispatchers.io) {
        val trackIds = ids.filterIsInstance<MediaId.Track>().map { it.trackId }
        val episodeIds = ids.filterIsInstance<MediaId.Episode>().map { it.episodeId }
        val tracksById = libraryDao.tracksByIds(trackIds).associateBy { it.id }
        val episodesById = if (episodeIds.isEmpty()) emptyMap() else podcastDao.episodesByIds(episodeIds).associateBy { it.id }
        // Episodes show their parent podcast's cover, so fetch the shows for the episodes in play.
        val showArtworkByPodcastId = if (episodesById.isEmpty()) {
            emptyMap()
        } else {
            val podcastIds = episodesById.values.map { it.podcastId }.distinct()
            podcastDao.podcastsByIds(podcastIds).associate { it.id to it.artworkHash }
        }
        // Preserve the requested order, dropping ids that no longer exist on disk.
        val resolved = ids.mapNotNull { id ->
            when (id) {
                is MediaId.Track -> tracksById[id.trackId]?.let(factory::forTrack)
                is MediaId.Episode -> episodesById[id.episodeId]?.let { episode ->
                    factory.forEpisode(episode, showArtworkByPodcastId[episode.podcastId])
                }
            }
        }
        if (resolved.size < ids.size) {
            log.debug("playback.resolve.dropped", mapOf("requested" to ids.size, "resolved" to resolved.size))
        }
        resolved
    }
}
