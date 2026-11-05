package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Query
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.SearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** FTS5 search over the library. All user input is sanitised before MATCH. */
@Dao
interface SearchDao {
    /**
     * Search tracks, albums, and artists in one shot. The raw query is turned
     * into quoted prefix phrases, so FTS operators and quotes in user input
     * are matched literally instead of being interpreted.
     */
    fun search(query: String): Flow<SearchResults> {
        val match = ftsMatchExpression(query) ?: return flowOf(SearchResults.EMPTY)
        return combine(
            observeTrackMatches(match),
            observeAlbumMatches("albumName: ($match)"),
            observeArtistMatches("artistName: ($match)")
        ) { tracks, albums, artists ->
            SearchResults(tracks = tracks, albums = albums, artists = artists)
        }
    }

    @Query(
        """
        SELECT t.* FROM tracks t
        JOIN tracks_fts f ON f.rowid = t.id
        WHERE tracks_fts MATCH :match
        ORDER BY t.title COLLATE NOCASE, t.id
        """
    )
    fun observeTrackMatches(match: String): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT DISTINCT a.* FROM albums a
        JOIN tracks t ON t.albumId = a.id
        JOIN tracks_fts f ON f.rowid = t.id
        WHERE tracks_fts MATCH :match
        ORDER BY a.name COLLATE NOCASE, a.id
        """
    )
    fun observeAlbumMatches(match: String): Flow<List<AlbumEntity>>

    @Query(
        """
        SELECT DISTINCT ar.* FROM artists ar
        JOIN tracks t ON t.albumArtistId = ar.id
        JOIN tracks_fts f ON f.rowid = t.id
        WHERE tracks_fts MATCH :match
        ORDER BY ar.name COLLATE NOCASE, ar.id
        """
    )
    fun observeArtistMatches(match: String): Flow<List<ArtistEntity>>

    /**
     * One-shot search for the car (search bar and voice): the same sanitised match as
     * [search], but each section in FTS rank order and capped at [limit]. Terms with no
     * letter or digit are dropped first, so punctuation-only input returns nothing.
     */
    suspend fun searchOnce(query: String, limit: Int): SearchResults {
        val match = ftsMatchExpression(query, wordTermsOnly = true) ?: return SearchResults.EMPTY
        return SearchResults(
            tracks = trackMatches(match, limit),
            albums = albumMatches("albumName: ($match)", limit),
            artists = artistMatches("artistName: ($match)", limit)
        )
    }

    /** Shows whose title contains [query]. Shows are not in the FTS table, and the podcast list is small. */
    suspend fun searchShows(query: String, limit: Int): List<PodcastEntity> {
        val pattern = likeContainsPattern(query) ?: return emptyList()
        return showMatches(pattern, limit)
    }

    @Query(
        """
        SELECT t.* FROM tracks t
        JOIN tracks_fts f ON f.rowid = t.id
        WHERE tracks_fts MATCH :match
        ORDER BY f.rank, t.id LIMIT :limit
        """
    )
    suspend fun trackMatches(match: String, limit: Int): List<TrackEntity>

    @Query(
        """
        SELECT a.* FROM albums a
        JOIN tracks t ON t.albumId = a.id
        JOIN tracks_fts f ON f.rowid = t.id
        WHERE tracks_fts MATCH :match
        GROUP BY a.id ORDER BY MIN(f.rank), a.id LIMIT :limit
        """
    )
    suspend fun albumMatches(match: String, limit: Int): List<AlbumEntity>

    @Query(
        """
        SELECT ar.* FROM artists ar
        JOIN tracks t ON t.albumArtistId = ar.id
        JOIN tracks_fts f ON f.rowid = t.id
        WHERE tracks_fts MATCH :match
        GROUP BY ar.id ORDER BY MIN(f.rank), ar.id LIMIT :limit
        """
    )
    suspend fun artistMatches(match: String, limit: Int): List<ArtistEntity>

    @Query("SELECT * FROM podcasts WHERE title LIKE :pattern ESCAPE '\\' ORDER BY title COLLATE NOCASE, id LIMIT :limit")
    suspend fun showMatches(pattern: String, limit: Int): List<PodcastEntity>
}

/**
 * Turn raw user input into a safe FTS5 MATCH expression: each whitespace-split
 * term becomes a double-quoted phrase (embedded quotes doubled) with a prefix
 * star, joined by implicit AND. With [wordTermsOnly], terms that hold no letter or
 * digit are dropped (the tokenizer would see an empty phrase). Returns null when
 * nothing searchable remains.
 */
internal fun ftsMatchExpression(raw: String, wordTermsOnly: Boolean = false): String? {
    val terms = raw.split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .filter { !wordTermsOnly || it.any(Char::isLetterOrDigit) }
    if (terms.isEmpty()) return null
    return terms.joinToString(" ") { term ->
        "\"" + term.replace("\"", "\"\"") + "\"*"
    }
}

/**
 * A LIKE pattern matching titles that contain [raw], with the LIKE wildcards and the
 * escape character itself escaped (`ESCAPE '\'`). Null when [raw] holds no letter or digit.
 */
internal fun likeContainsPattern(raw: String): String? {
    val text = raw.trim()
    if (text.none(Char::isLetterOrDigit)) return null
    val escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "%$escaped%"
}
