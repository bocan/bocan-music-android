package io.cloudcauldron.bocan.persistence.model

import androidx.room3.Embedded
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import java.time.Instant

/** An episode joined with the phone's own listening progress. */
data class EpisodeWithProgress(@Embedded val episode: EpisodeEntity, val playPositionMs: Long, val lastPlayedAt: Instant?)
