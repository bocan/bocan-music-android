package io.cloudcauldron.bocan.playback.browse

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.persistence.daos.BrowseDao
import io.cloudcauldron.bocan.persistence.daos.SearchDao
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.NameRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** The labels every browse test uses; the bucket titles differ from their keys so a test can tell them apart. */
internal val TEST_LABELS = BrowseLabels(
    home = "Home",
    recentlyPlayed = "Recently Played",
    continueListening = "Continue",
    playlists = "Playlists",
    albums = "Albums",
    artists = "Artists",
    podcasts = "Podcasts",
    songs = "Songs",
    digitsBucket = "Digits",
    otherBucket = "Other"
)

/** An [AppLog] that keeps warnings so a test can assert on them. */
internal class RecordingLog : AppLog {
    val warnings = mutableListOf<Pair<String, Map<String, Any?>>>()

    override fun debug(event: String, fields: Map<String, Any?>) = Unit
    override fun info(event: String, fields: Map<String, Any?>) = Unit
    override fun warning(event: String, fields: Map<String, Any?>) {
        warnings += event to fields
    }
    override fun error(event: String, fields: Map<String, Any?>) = Unit
}

/** A hand-fed [BrowseDao] so the tree and search logic is tested without a real database. */
internal data class FakeBrowseDao(
    private val albums: List<AlbumEntity> = emptyList(),
    private val artists: List<ArtistEntity> = emptyList(),
    private val shows: List<PodcastEntity> = emptyList(),
    private val playlists: List<PlaylistEntity> = emptyList(),
    private val songs: List<TrackEntity> = emptyList(),
    private val recentlyPlayed: List<TrackEntity> = emptyList(),
    private val albumTracks: Map<Long, List<TrackEntity>> = emptyMap(),
    private val artistTracks: Map<Long, List<TrackEntity>> = emptyMap(),
    private val playlistTracks: Map<Long, List<TrackEntity>> = emptyMap(),
    private val episodesByShow: Map<Long, List<EpisodeEntity>> = emptyMap(),
    private val continueListening: List<EpisodeEntity> = emptyList()
) : BrowseDao {
    var lastPlaylistsPaging: Pair<Int, Int>? = null

    fun copyWith(artistTracks: Map<Long, List<TrackEntity>>): FakeBrowseDao = copy(artistTracks = artistTracks)

    private fun <T> List<T>.page(limit: Int, offset: Int): List<T> = drop(offset).take(limit)

    override suspend fun albumsPage(limit: Int, offset: Int) = albums.page(limit, offset)
    override suspend fun artistsPage(limit: Int, offset: Int) = artists.page(limit, offset)
    override suspend fun showsPage(limit: Int, offset: Int) = shows.page(limit, offset)
    override suspend fun playlistsPage(limit: Int, offset: Int): List<PlaylistEntity> {
        lastPlaylistsPaging = limit to offset
        return playlists.page(limit, offset)
    }

    override suspend fun recentSongsPage(limit: Int, offset: Int) = songs.page(limit, offset)
    override suspend fun albumTracksPage(albumId: Long, limit: Int, offset: Int) = albumTracks[albumId].orEmpty().page(limit, offset)
    override suspend fun artistTracksPage(artistId: Long, limit: Int, offset: Int) = artistTracks[artistId].orEmpty().page(limit, offset)
    override suspend fun playlistTracksPage(playlistId: Long, limit: Int, offset: Int) =
        playlistTracks[playlistId].orEmpty().page(limit, offset)

    override suspend fun episodesPage(podcastId: Long, limit: Int, offset: Int) = episodesByShow[podcastId].orEmpty().page(limit, offset)
    override suspend fun continueListeningPage(limit: Int, offset: Int) = continueListening.page(limit, offset)
    override suspend fun artistNames() = artists.map { NameRow(it.id, it.name) }
    override suspend fun albumNames() = albums.map { NameRow(it.id, it.name) }
    override suspend fun artistsByIds(ids: List<Long>) = artists.filter { it.id in ids }.sortedBy { it.name.lowercase() }
    override suspend fun albumsByIds(ids: List<Long>) = albums.filter { it.id in ids }.sortedBy { it.name.lowercase() }
    override suspend fun recentlyPlayedPage(limit: Int, offset: Int) = recentlyPlayed.page(limit, offset)
    override suspend fun downloadedTrackIds() = songs.map { it.id }
}

/**
 * A [SearchDao] faked at the SQL boundary only: the real `searchOnce` and `searchShows`
 * defaults run, with the real sanitiser, and this fake answers the queries they build.
 * A term matches when a word of the name starts with it, close to FTS5 prefix phrases.
 * Rows come back in list order, which stands in for FTS rank.
 */
internal class FakeSearchDao(
    private val tracks: List<TrackEntity> = emptyList(),
    private val albums: List<AlbumEntity> = emptyList(),
    private val artists: List<ArtistEntity> = emptyList(),
    private val shows: List<PodcastEntity> = emptyList()
) : SearchDao {
    override fun observeTrackMatches(match: String): Flow<List<TrackEntity>> = emptyFlow()
    override fun observeAlbumMatches(match: String): Flow<List<AlbumEntity>> = emptyFlow()
    override fun observeArtistMatches(match: String): Flow<List<ArtistEntity>> = emptyFlow()

    override suspend fun trackMatches(match: String, limit: Int) =
        tracks.filter { matches(match, it.title, it.artistName, it.albumName) }.take(limit)

    override suspend fun albumMatches(match: String, limit: Int) = albums.filter { matches(match, it.name) }.take(limit)
    override suspend fun artistMatches(match: String, limit: Int) = artists.filter { matches(match, it.name) }.take(limit)

    override suspend fun showMatches(pattern: String, limit: Int): List<PodcastEntity> {
        val needle = pattern.removeSurrounding("%").replace("\\%", "%").replace("\\_", "_").replace("\\\\", "\\")
        return shows.filter { it.title.contains(needle, ignoreCase = true) }.take(limit)
    }

    private fun matches(match: String, vararg fields: String): Boolean {
        val terms = TERM.findAll(match).map { it.groupValues[1].replace("\"\"", "\"").lowercase() }.toList()
        val words = fields.flatMap { it.lowercase().split(Regex("\\W+")) }
        return terms.isNotEmpty() && terms.all { term -> words.any { it.startsWith(term) } }
    }

    private companion object {
        val TERM = Regex("\"((?:[^\"]|\"\")*)\"\\*")
    }
}
