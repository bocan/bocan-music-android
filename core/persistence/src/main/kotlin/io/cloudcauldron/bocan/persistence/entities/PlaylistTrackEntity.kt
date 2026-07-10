package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.Index

/** Ordered playlist membership; position is the 0-based manifest order. */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "position"],
    indices = [Index("trackId")]
)
data class PlaylistTrackEntity(val playlistId: Long, val position: Int, val trackId: Long)
