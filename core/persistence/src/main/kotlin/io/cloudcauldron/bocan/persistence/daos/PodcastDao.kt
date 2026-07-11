package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Query
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.model.EpisodeProgressRow
import io.cloudcauldron.bocan.persistence.model.EpisodeWithProgress
import io.cloudcauldron.bocan.persistence.model.UnplayedCount
import kotlinx.coroutines.flow.Flow

/** Reactive reads over synced podcasts and phone-owned listening progress. */
@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE, id")
    fun observeShows(): Flow<List<PodcastEntity>>

    fun observeEpisodes(podcastId: Long, sortNewestFirst: Boolean): Flow<List<EpisodeEntity>> =
        if (sortNewestFirst) observeEpisodesNewestFirst(podcastId) else observeEpisodesOldestFirst(podcastId)

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC, id")
    fun observeEpisodesNewestFirst(podcastId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt ASC, id")
    fun observeEpisodesOldestFirst(podcastId: Long): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT e.*, s.playPositionMs AS playPositionMs, s.lastPlayedAt AS lastPlayedAt
        FROM episodes e
        JOIN episode_state s ON s.episodeId = e.id
        WHERE s.playState = 'inProgress'
        ORDER BY s.lastPlayedAt DESC
        LIMIT 20
        """
    )
    fun observeContinueListening(): Flow<List<EpisodeWithProgress>>

    /** Unplayed episode count per show: episodes with no state row or an unplayed state. */
    @Query(
        """
        SELECT e.podcastId AS podcastId, COUNT(*) AS unplayed
        FROM episodes e
        LEFT JOIN episode_state s ON s.episodeId = e.id
        WHERE s.playState IS NULL OR s.playState = 'unplayed'
        GROUP BY e.podcastId
        """
    )
    fun observeUnplayedCounts(): Flow<List<UnplayedCount>>

    @Query("SELECT * FROM podcasts WHERE id = :podcastId")
    fun observeShow(podcastId: Long): Flow<PodcastEntity?>

    /** Episodes of a show newest-first, each with its phone-owned progress (null when never played). */
    @Query(
        """
        SELECT e.*, s.playState AS playStateWire, s.playPositionMs AS playPositionMs
        FROM episodes e
        LEFT JOIN episode_state s ON s.episodeId = e.id
        WHERE e.podcastId = :podcastId
        ORDER BY e.publishedAt DESC, e.id
        """
    )
    fun observeEpisodesWithState(podcastId: Long): Flow<List<EpisodeProgressRow>>

    @Query("SELECT * FROM episodes WHERE id IN (:ids)")
    suspend fun episodesByIds(ids: List<String>): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun episode(episodeId: String): EpisodeEntity?
}
