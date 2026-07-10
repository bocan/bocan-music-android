package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.cloudcauldron.bocan.persistence.model.PlayState
import java.time.Instant

/**
 * Phone-owned listening progress for an episode. Seeded once from the synced
 * episode's seed fields when the row is first created; never re-seeded.
 */
@Entity(tableName = "episode_state")
data class EpisodeStateEntity(
    @PrimaryKey val episodeId: String,
    val playPositionMs: Long = 0,
    val playState: PlayState = PlayState.Unplayed,
    val lastPlayedAt: Instant? = null,
    val completedAt: Instant? = null,
    val speedOverride: Double? = null
)
