package io.cloudcauldron.bocan.app.podcasts

import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.model.EpisodeProgressRow
import io.cloudcauldron.bocan.persistence.model.EpisodeWithProgress
import io.cloudcauldron.bocan.persistence.model.PlayState
import java.time.Instant

/** A subscribed show as the grid renders it, with its unplayed-episode badge count. */
data class ShowUi(val id: Long, val title: String, val author: String?, val artworkHash: String?, val unplayedCount: Int)

/** An episode's listening state for its row indicator. */
sealed interface EpisodeProgressUi {
    data object Unplayed : EpisodeProgressUi
    data class InProgress(val progress: Float, val remainingMs: Long) : EpisodeProgressUi
    data object Played : EpisodeProgressUi
}

/** One episode as the show detail list renders it; formatting of dates and durations is left to the UI. */
data class EpisodeUi(
    val id: String,
    val title: String,
    val publishedAt: Instant,
    val durationMs: Long,
    val podcastId: Long,
    val hasChapters: Boolean,
    val descriptionHtml: String?,
    val progress: EpisodeProgressUi
)

/** A continue-listening card: an in-progress episode with its remaining time and progress ring. */
data class ContinueUi(val episodeId: String, val title: String, val progress: Float, val remainingMs: Long)

fun PodcastEntity.toUi(unplayed: Int): ShowUi = ShowUi(id, title, author, artworkHash, unplayed)

fun EpisodeProgressRow.toUi(): EpisodeUi = EpisodeUi(
    id = episode.id,
    title = episode.title,
    publishedAt = episode.publishedAt,
    durationMs = episode.durationMs,
    podcastId = episode.podcastId,
    hasChapters = episode.hasChapters,
    descriptionHtml = episode.descriptionHtml,
    progress = progressOf(PlayState.fromWireOrNull(playStateWire.orEmpty()), playPositionMs ?: 0, episode.durationMs)
)

fun EpisodeWithProgress.toContinueUi(): ContinueUi = ContinueUi(
    episodeId = episode.id,
    title = episode.title,
    progress = fractionOf(playPositionMs, episode.durationMs),
    remainingMs = (episode.durationMs - playPositionMs).coerceAtLeast(0)
)

private fun progressOf(state: PlayState?, positionMs: Long, durationMs: Long): EpisodeProgressUi = when (state) {
    PlayState.Played -> EpisodeProgressUi.Played
    PlayState.InProgress -> EpisodeProgressUi.InProgress(fractionOf(positionMs, durationMs), (durationMs - positionMs).coerceAtLeast(0))
    else -> EpisodeProgressUi.Unplayed
}

private fun fractionOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
