package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.cloudcauldron.bocan.persistence.model.PlaylistKind

/** A synced playlist. Folders carry hierarchy only and have no member rows. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val kind: PlaylistKind,
    val parentId: Long?,
    val sortOrder: Int,
    val accentColor: String?,
    val artworkHash: String?
)
