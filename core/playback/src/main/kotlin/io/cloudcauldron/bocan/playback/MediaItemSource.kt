package io.cloudcauldron.bocan.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.persistence.daos.LibraryDao
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
}

/**
 * The database-backed [MediaItemSource]. Tracks are looked up in one query and
 * mapped through [MediaItemFactory], preserving the caller's requested order. Episode
 * ids are not resolved here yet (podcasts arrive in phase 07); they are logged and
 * skipped rather than silently dropped.
 */
@UnstableApi
class DatabaseMediaItemSource(
    private val libraryDao: LibraryDao,
    private val factory: MediaItemFactory,
    private val dispatchers: CoroutineDispatchers,
    private val log: AppLog
) : MediaItemSource {
    override suspend fun resolve(ids: List<MediaId>): List<MediaItem> = withContext(dispatchers.io) {
        val trackIds = ids.filterIsInstance<MediaId.Track>().map { it.trackId }
        val episodeIds = ids.filterIsInstance<MediaId.Episode>()
        if (episodeIds.isNotEmpty()) {
            log.debug("playback.resolve.episodesSkipped", mapOf("count" to episodeIds.size))
        }
        val byId = libraryDao.tracksByIds(trackIds).associateBy { it.id }
        // Preserve the requested order, dropping ids that no longer exist on disk.
        ids.mapNotNull { id ->
            when (id) {
                is MediaId.Track -> byId[id.trackId]?.let(factory::forTrack)
                is MediaId.Episode -> null
            }
        }
    }
}
