package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Derived from the manifest's tracks during apply: grouped by albumId with the
 * minimum year and the first non-null artwork hash in deterministic track order.
 */
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val albumArtistName: String,
    val year: Int?,
    val artworkHash: String?,
    val trackCount: Int
)
