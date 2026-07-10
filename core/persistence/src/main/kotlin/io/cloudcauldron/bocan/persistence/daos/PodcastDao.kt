package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Query
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.model.EpisodeWithProgress
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
}
