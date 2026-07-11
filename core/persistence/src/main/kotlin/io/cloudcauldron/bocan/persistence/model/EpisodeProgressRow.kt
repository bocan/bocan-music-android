package io.cloudcauldron.bocan.persistence.model

import androidx.room3.Embedded
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity

/**
 * An episode with its phone-owned progress from a LEFT JOIN, for the show detail list.
 * [playStateWire] and [playPositionMs] are null when the episode has no state row yet
 * (never played); the wire string maps back to a [PlayState] via [PlayState.fromWireOrNull].
 */
data class EpisodeProgressRow(@Embedded val episode: EpisodeEntity, val playStateWire: String?, val playPositionMs: Long?)
