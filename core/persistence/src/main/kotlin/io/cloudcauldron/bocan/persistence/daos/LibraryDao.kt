package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Query
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.AlbumSort
import io.cloudcauldron.bocan.persistence.model.ArtistTrackCount
import io.cloudcauldron.bocan.persistence.model.DownloadCounts
import io.cloudcauldron.bocan.persistence.model.TrackSort
import kotlinx.coroutines.flow.Flow

/** Reactive reads over the synced library tables. */
@Dao
interface LibraryDao {
    fun observeAlbums(sort: AlbumSort): Flow<List<AlbumEntity>> = when (sort) {
        AlbumSort.Name -> observeAlbumsByName()
        AlbumSort.Artist -> observeAlbumsByArtist()
        AlbumSort.Year -> observeAlbumsByYear()
    }

    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE, id")
    fun observeAlbumsByName(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY albumArtistName COLLATE NOCASE, year, name COLLATE NOCASE, id")
    fun observeAlbumsByArtist(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY year DESC, name COLLATE NOCASE, id")
    fun observeAlbumsByYear(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE, id")
    fun observeArtists(): Flow<List<ArtistEntity>>

    /** Per-artist track counts computed in SQL, so the artists list never loads every track. */
    @Query("SELECT albumArtistId AS artistId, COUNT(*) AS count FROM tracks GROUP BY albumArtistId")
    fun observeArtistTrackCounts(): Flow<List<ArtistTrackCount>>

    @Query(
        """
        SELECT * FROM tracks WHERE albumId = :albumId
        ORDER BY COALESCE(discNumber, 1), COALESCE(trackNumber, 2147483647), title COLLATE NOCASE, id
        """
    )
    fun observeTracksForAlbum(albumId: Long): Flow<List<TrackEntity>>

    fun observeAllTracks(sort: TrackSort): Flow<List<TrackEntity>> = when (sort) {
        TrackSort.Title -> observeAllTracksByTitle()
        TrackSort.Artist -> observeAllTracksByArtist()
        TrackSort.Album -> observeAllTracksByAlbum()
    }

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE, id")
    fun observeAllTracksByTitle(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks
        ORDER BY artistName COLLATE NOCASE, albumName COLLATE NOCASE,
            COALESCE(discNumber, 1), COALESCE(trackNumber, 2147483647), id
        """
    )
    fun observeAllTracksByArtist(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks
        ORDER BY albumName COLLATE NOCASE, COALESCE(discNumber, 1), COALESCE(trackNumber, 2147483647), id
        """
    )
    fun observeAllTracksByAlbum(): Flow<List<TrackEntity>>

    @Query("SELECT DISTINCT genre FROM tracks WHERE genre IS NOT NULL ORDER BY genre COLLATE NOCASE")
    fun observeGenres(): Flow<List<String>>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun tracksByIds(ids: List<Long>): List<TrackEntity>

    /** Ids of every downloaded track, for the Shuffle library shortcut. */
    @Query("SELECT id FROM tracks WHERE downloadState = 'downloaded' ORDER BY id")
    suspend fun downloadedTrackIds(): List<Long>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN downloadState = 'pending' THEN 1 ELSE 0 END), 0) AS pending,
            COALESCE(SUM(CASE WHEN downloadState = 'downloaded' THEN 1 ELSE 0 END), 0) AS downloaded,
            COALESCE(SUM(CASE WHEN downloadState = 'failed' THEN 1 ELSE 0 END), 0) AS failed
        FROM tracks
        """
    )
    fun observeDownloadCounts(): Flow<DownloadCounts>
}
