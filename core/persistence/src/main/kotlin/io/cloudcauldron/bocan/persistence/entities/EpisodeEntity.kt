package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.model.PlayState
import java.time.Instant

/**
 * A synced podcast episode. seedPositionMs and seedPlayState are the Mac's
 * values at manifest time; they only ever seed a missing episode_state row,
 * after which the phone owns its own listening progress.
 */
@Entity(
    tableName = "episodes",
    indices = [
        Index("podcastId"),
        Index("downloadState")
    ]
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val publishedAt: Instant,
    val durationMs: Long,
    val descriptionHtml: String?,
    val relPath: String,
    val size: Long,
    val sha256: String,
    val hasChapters: Boolean,
    val downloadState: DownloadState,
    val syncedAt: Instant,
    val seedPositionMs: Long,
    val seedPlayState: PlayState
)
