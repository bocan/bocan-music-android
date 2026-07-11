package io.cloudcauldron.bocan.app.podcasts

import io.cloudcauldron.bocan.app.data.PodcastPreferencesSource
import io.cloudcauldron.bocan.persistence.daos.EpisodeStateDao
import io.cloudcauldron.bocan.persistence.daos.PodcastDao
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeStateEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.model.EpisodeProgressRow
import io.cloudcauldron.bocan.persistence.model.EpisodeWithProgress
import io.cloudcauldron.bocan.persistence.model.PlayState
import io.cloudcauldron.bocan.persistence.model.UnplayedCount
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A show entity with sensible defaults for tests. */
fun podcast(id: Long, title: String = "Show $id", defaultSpeed: Double? = null): PodcastEntity =
    PodcastEntity(id, title, author = "Author $id", descriptionHtml = null, artworkHash = null, defaultSpeed = defaultSpeed)

/** An episode entity with sensible defaults for tests. */
fun episode(
    id: String,
    podcastId: Long,
    title: String = "Episode $id",
    publishedAt: Instant = Instant.EPOCH,
    durationMs: Long = 600_000,
    hasChapters: Boolean = false
): EpisodeEntity = EpisodeEntity(
    id = id,
    podcastId = podcastId,
    guid = id,
    title = title,
    publishedAt = publishedAt,
    durationMs = durationMs,
    descriptionHtml = null,
    relPath = "episodes/$id.mp3",
    size = 1,
    sha256 = "0".repeat(64),
    hasChapters = hasChapters,
    downloadState = DownloadState.Downloaded,
    syncedAt = Instant.EPOCH,
    seedPositionMs = 0,
    seedPlayState = PlayState.Unplayed
)

/**
 * A hand-driven PodcastDao: the reactive reads back onto MutableStateFlows tests push into,
 * and the suspend point-reads answer from an in-memory episode map.
 */
class FakePodcastDao : PodcastDao {
    val shows = MutableStateFlow<List<PodcastEntity>>(emptyList())
    val continueListening = MutableStateFlow<List<EpisodeWithProgress>>(emptyList())
    val unplayedCounts = MutableStateFlow<List<UnplayedCount>>(emptyList())
    val episodesWithState = MutableStateFlow<List<EpisodeProgressRow>>(emptyList())
    val episodes = mutableMapOf<String, EpisodeEntity>()

    override fun observeShows(): Flow<List<PodcastEntity>> = shows
    override fun observeEpisodesNewestFirst(podcastId: Long): Flow<List<EpisodeEntity>> =
        MutableStateFlow(episodes.values.filter { it.podcastId == podcastId })
    override fun observeEpisodesOldestFirst(podcastId: Long): Flow<List<EpisodeEntity>> = observeEpisodesNewestFirst(podcastId)
    override fun observeContinueListening(): Flow<List<EpisodeWithProgress>> = continueListening
    override fun observeUnplayedCounts(): Flow<List<UnplayedCount>> = unplayedCounts
    override fun observeShow(podcastId: Long): Flow<PodcastEntity?> = MutableStateFlow(shows.value.firstOrNull { it.id == podcastId })
    override fun observeEpisodesWithState(podcastId: Long): Flow<List<EpisodeProgressRow>> = episodesWithState
    override suspend fun episodesByIds(ids: List<String>): List<EpisodeEntity> = ids.mapNotNull { episodes[it] }
    override suspend fun episode(episodeId: String): EpisodeEntity? = episodes[episodeId]
}

/** An in-memory EpisodeStateDao recording every write for assertions. */
class FakeEpisodeStateDao : EpisodeStateDao {
    val states = mutableMapOf<String, EpisodeStateEntity>()

    override fun observeState(episodeId: String): Flow<EpisodeStateEntity?> = MutableStateFlow(states[episodeId])
    override suspend fun state(episodeId: String): EpisodeStateEntity? = states[episodeId]
    override suspend fun setSpeedOverride(episodeId: String, speed: Double?) {
        states[episodeId] = (states[episodeId] ?: EpisodeStateEntity(episodeId)).copy(speedOverride = speed)
    }
    override suspend fun upsert(state: EpisodeStateEntity) {
        states[state.episodeId] = state
    }
}

/** A DataStore-free PodcastPreferencesSource backed by MutableStateFlows. */
class FakePodcastPreferences : PodcastPreferencesSource {
    override val appDefaultSpeed = MutableStateFlow(1.0)
    override val skipBackSeconds = MutableStateFlow(15)
    override val skipForwardSeconds = MutableStateFlow(30)
    private val showSpeeds = mutableMapOf<Long, MutableStateFlow<Double?>>()

    override fun showSpeed(podcastId: Long): Flow<Double?> = showSpeeds.getOrPut(podcastId) { MutableStateFlow(null) }

    /** The current per-show override value, for test assertions. */
    fun showSpeedValue(podcastId: Long): Double? = showSpeeds[podcastId]?.value

    override suspend fun setAppDefaultSpeed(speed: Double) {
        appDefaultSpeed.value = speed
    }
    override suspend fun setSkipBackSeconds(seconds: Int) {
        skipBackSeconds.value = seconds
    }
    override suspend fun setSkipForwardSeconds(seconds: Int) {
        skipForwardSeconds.value = seconds
    }
    override suspend fun setShowSpeed(podcastId: Long, speed: Double?) {
        showSpeeds.getOrPut(podcastId) { MutableStateFlow(null) }.value = speed
    }
}
