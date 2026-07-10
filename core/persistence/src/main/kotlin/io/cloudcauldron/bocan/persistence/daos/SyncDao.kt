package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeStateEntity
import io.cloudcauldron.bocan.persistence.entities.PlayStatsEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistTrackEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.DownloadState
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The sync engine's persistence surface: the paired server row plus the bulk
 * write operations SyncApplier composes inside one transaction. Nothing else
 * writes the synced tables.
 */
@Dao
interface SyncDao {
    // Paired server

    @Query("SELECT * FROM sync_server LIMIT 1")
    fun observeServer(): Flow<SyncServerEntity?>

    @Query("SELECT * FROM sync_server LIMIT 1")
    suspend fun server(): SyncServerEntity?

    @Transaction
    suspend fun replaceServer(server: SyncServerEntity) {
        clearServer()
        insertServer(server)
    }

    @Query("DELETE FROM sync_server")
    suspend fun clearServer()

    @Insert
    suspend fun insertServer(server: SyncServerEntity)

    @Query("UPDATE sync_server SET lastAppliedGeneration = :generation, lastSyncAt = :at")
    suspend fun recordApplied(generation: Long, at: Instant)

    // Tracks

    @Query("SELECT * FROM tracks")
    suspend fun allTracks(): List<TrackEntity>

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracks(ids: List<Long>)

    @Query("UPDATE tracks SET downloadState = :state WHERE id IN (:ids) OR clipSourceTrackId IN (:ids)")
    suspend fun setTrackDownloadState(ids: List<Long>, state: DownloadState)

    // Derived albums and artists

    @Query("DELETE FROM albums")
    suspend fun clearAlbums()

    @Insert
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Query("DELETE FROM artists")
    suspend fun clearArtists()

    @Insert
    suspend fun insertArtists(artists: List<ArtistEntity>)

    // Playlists

    @Query("DELETE FROM playlists")
    suspend fun clearPlaylists()

    @Query("DELETE FROM playlist_tracks")
    suspend fun clearPlaylistTracks()

    @Insert
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Insert
    suspend fun insertPlaylistTracks(rows: List<PlaylistTrackEntity>)

    // Podcasts and episodes

    @Query("SELECT * FROM podcasts")
    suspend fun allPodcasts(): List<PodcastEntity>

    @Upsert
    suspend fun upsertPodcasts(podcasts: List<PodcastEntity>)

    @Query("DELETE FROM podcasts WHERE id IN (:ids)")
    suspend fun deletePodcasts(ids: List<Long>)

    @Query("SELECT * FROM episodes")
    suspend fun allEpisodes(): List<EpisodeEntity>

    @Upsert
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE id IN (:ids)")
    suspend fun deleteEpisodes(ids: List<String>)

    @Query("UPDATE episodes SET downloadState = :state WHERE id IN (:ids)")
    suspend fun setEpisodeDownloadState(ids: List<String>, state: DownloadState)

    // Local-state seeding: insert-if-missing, never overwrite

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedPlayStats(rows: List<PlayStatsEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedEpisodeStates(rows: List<EpisodeStateEntity>)

    // Artwork the local library already references

    @Query(
        """
        SELECT artworkHash FROM tracks WHERE artworkHash IS NOT NULL
        UNION SELECT artworkHash FROM playlists WHERE artworkHash IS NOT NULL
        UNION SELECT artworkHash FROM podcasts WHERE artworkHash IS NOT NULL
        """
    )
    suspend fun knownArtworkHashes(): List<String>
}
