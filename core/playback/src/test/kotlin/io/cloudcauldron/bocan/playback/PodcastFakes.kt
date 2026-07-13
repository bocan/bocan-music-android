package io.cloudcauldron.bocan.playback

import io.cloudcauldron.bocan.persistence.daos.EpisodeStateDao
import io.cloudcauldron.bocan.persistence.daos.PodcastDao
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeStateEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.model.EpisodeProgressRow
import io.cloudcauldron.bocan.persistence.model.EpisodeWithProgress
import io.cloudcauldron.bocan.persistence.model.UnplayedCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** Minimal PodcastDao fake for playback tests; answers episode and show lookups by id. */
class FakePodcastDao(private val episodes: List<EpisodeEntity> = emptyList(), private val podcasts: List<PodcastEntity> = emptyList()) :
    PodcastDao {
    override fun observeShows(): Flow<List<PodcastEntity>> = emptyFlow()
    override fun observeEpisodesNewestFirst(podcastId: Long): Flow<List<EpisodeEntity>> = emptyFlow()
    override fun observeEpisodesOldestFirst(podcastId: Long): Flow<List<EpisodeEntity>> = emptyFlow()
    override fun observeContinueListening(): Flow<List<EpisodeWithProgress>> = emptyFlow()
    override fun observeUnplayedCounts(): Flow<List<UnplayedCount>> = flowOf(emptyList())
    override fun observeShow(podcastId: Long): Flow<PodcastEntity?> = flowOf(null)
    override fun observeEpisodesWithState(podcastId: Long): Flow<List<EpisodeProgressRow>> = flowOf(emptyList())
    override suspend fun episodesByIds(ids: List<String>): List<EpisodeEntity> = episodes.filter { it.id in ids }
    override suspend fun podcastsByIds(ids: List<Long>): List<PodcastEntity> = podcasts.filter { it.id in ids }
    override suspend fun episode(episodeId: String): EpisodeEntity? = episodes.firstOrNull { it.id == episodeId }
}

/** In-memory EpisodeStateDao fake for playback tests. */
class FakeEpisodeStateDao : EpisodeStateDao {
    private val rows = HashMap<String, EpisodeStateEntity>()
    override fun observeState(episodeId: String): Flow<EpisodeStateEntity?> = flowOf(rows[episodeId])
    override suspend fun state(episodeId: String): EpisodeStateEntity? = rows[episodeId]
    override suspend fun setSpeedOverride(episodeId: String, speed: Double?) {
        rows[episodeId]?.let { rows[episodeId] = it.copy(speedOverride = speed) }
    }
    override suspend fun upsert(state: EpisodeStateEntity) {
        rows[state.episodeId] = state
    }
}
