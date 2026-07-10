package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** A synced podcast show. defaultSpeed is the Mac's per-show playback override. */
@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val author: String?,
    val descriptionHtml: String?,
    val artworkHash: String?,
    val defaultSpeed: Double?
)
