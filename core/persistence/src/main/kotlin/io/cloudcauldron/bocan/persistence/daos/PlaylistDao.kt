package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Query
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import kotlinx.coroutines.flow.Flow

/** Reactive reads over synced playlists. */
@Dao
interface PlaylistDao {
    /** All playlists, parents before their children, siblings in sort order. */
    @Query("SELECT * FROM playlists ORDER BY COALESCE(parentId, -1), sortOrder, name COLLATE NOCASE, id")
    fun observePlaylistTree(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT t.* FROM playlist_tracks pt
        JOIN tracks t ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position
        """
    )
    fun observeTracksIn(playlistId: Long): Flow<List<TrackEntity>>
}
