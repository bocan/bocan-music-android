package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.Fts5

/**
 * FTS5 external-content index over tracks. Room generates the sync triggers;
 * all writes go to the tracks table. Prefix indexes cover the search box's
 * short-prefix matching without a scan.
 */
@Entity(tableName = "tracks_fts")
@Fts5(
    contentEntity = TrackEntity::class,
    contentRowId = "id",
    prefix = [2, 3]
)
data class TrackFtsEntity(val title: String, val artistName: String, val albumName: String, val genre: String?)
