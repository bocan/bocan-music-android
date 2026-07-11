package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Query
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity

/**
 * Paged, snapshot reads for the Android Auto browse tree. Auto calls onGetChildren with a
 * page and page size and expects a fast answer, so these are LIMIT/OFFSET queries served
 * straight from indices (phase 10 gotcha: never compute folder trees on demand). Only
 * downloaded tracks and episodes are browsable, since Auto can only play local files.
 */
@Dao
interface BrowseDao {
    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE, id LIMIT :limit OFFSET :offset")
    suspend fun albumsPage(limit: Int, offset: Int): List<AlbumEntity>

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE, id LIMIT :limit OFFSET :offset")
    suspend fun artistsPage(limit: Int, offset: Int): List<ArtistEntity>

    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE, id LIMIT :limit OFFSET :offset")
    suspend fun showsPage(limit: Int, offset: Int): List<PodcastEntity>

    @Query("SELECT * FROM playlists WHERE parentId IS NULL ORDER BY sortOrder, name COLLATE NOCASE, id LIMIT :limit OFFSET :offset")
    suspend fun playlistsPage(limit: Int, offset: Int): List<PlaylistEntity>

    @Query(
        """
        SELECT * FROM tracks WHERE downloadState = 'downloaded'
        ORDER BY syncedAt DESC, title COLLATE NOCASE, id LIMIT :limit OFFSET :offset
        """
    )
    suspend fun recentSongsPage(limit: Int, offset: Int): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks WHERE albumId = :albumId AND downloadState = 'downloaded'
        ORDER BY discNumber, trackNumber, title COLLATE NOCASE, id LIMIT :limit OFFSET :offset
        """
    )
    suspend fun albumTracksPage(albumId: Long, limit: Int, offset: Int): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks WHERE artistId = :artistId AND downloadState = 'downloaded'
        ORDER BY albumName COLLATE NOCASE, discNumber, trackNumber, id LIMIT :limit OFFSET :offset
        """
    )
    suspend fun artistTracksPage(artistId: Long, limit: Int, offset: Int): List<TrackEntity>

    @Query(
        """
        SELECT t.* FROM playlist_tracks pt JOIN tracks t ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId AND t.downloadState = 'downloaded'
        ORDER BY pt.position LIMIT :limit OFFSET :offset
        """
    )
    suspend fun playlistTracksPage(playlistId: Long, limit: Int, offset: Int): List<TrackEntity>

    @Query(
        """
        SELECT * FROM episodes WHERE podcastId = :podcastId AND downloadState = 'downloaded'
        ORDER BY publishedAt DESC, id LIMIT :limit OFFSET :offset
        """
    )
    suspend fun episodesPage(podcastId: Long, limit: Int, offset: Int): List<EpisodeEntity>

    @Query(
        """
        SELECT e.* FROM episode_state s JOIN episodes e ON e.id = s.episodeId
        WHERE s.playState = 'inProgress' AND e.downloadState = 'downloaded'
        ORDER BY s.lastPlayedAt DESC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun continueListeningPage(limit: Int, offset: Int): List<EpisodeEntity>
}
