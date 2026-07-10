package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** Derived from the manifest's album artists during apply. */
@Entity(tableName = "artists")
data class ArtistEntity(@PrimaryKey val id: Long, val name: String)
