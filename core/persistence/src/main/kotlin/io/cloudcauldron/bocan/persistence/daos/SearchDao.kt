package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Query
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
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
}

/**
 * Turn raw user input into a safe FTS5 MATCH expression: each whitespace-split
 * term becomes a double-quoted phrase (embedded quotes doubled) with a prefix
 * star, joined by implicit AND. Returns null when nothing searchable remains.
 */
internal fun ftsMatchExpression(raw: String): String? {
    val terms = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return null
    return terms.joinToString(" ") { term ->
        "\"" + term.replace("\"", "\"\"") + "\"*"
    }
}
