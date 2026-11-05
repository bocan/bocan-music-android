package io.cloudcauldron.bocan.playback.browse

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import io.cloudcauldron.bocan.persistence.daos.BrowseDao
import io.cloudcauldron.bocan.persistence.daos.SearchDao
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaId
import kotlinx.coroutines.withContext

/**
 * Search for the car (phase 15): the head unit's search bar and "play X on Bòcan".
 *
 * [search] runs the sanitised FTS match once and returns one flat list in four sections,
 * artists, albums, songs, shows, each item carrying its section's group title so the car
 * draws headers. Within a section an exact name match comes first, then a prefix match,
 * then FTS rank order. Only downloaded songs are offered, because the car plays local files.
 *
 * [resolveVoice] turns a spoken query into the ids to play, through the same match. Search
 * never throws for bad input: a query that sanitises to nothing returns an empty list.
 */
@UnstableApi
class BrowseSearch(
    private val searchDao: SearchDao,
    private val browseDao: BrowseDao,
    private val style: BrowseStyle,
    private val labels: BrowseLabels,
    private val dispatchers: CoroutineDispatchers
) {
    /** Sectioned results for [query], at most [perSection] per section. Never paged: sections need the whole list. */
    suspend fun search(query: String, perSection: Int = PER_SECTION): List<MediaItem> = withContext(dispatchers.io) {
        val text = query.trim()
        val fetch = perSection * OVERFETCH
        val results = searchDao.searchOnce(text, fetch)
        val shows = searchDao.searchShows(text, fetch)
        val songs = results.tracks.filter { it.downloadState == DownloadState.Downloaded }
        buildList {
            ranked(results.artists, text) { it.name }.take(perSection).forEach {
                add(style.folder("${MediaTree.PREFIX_ARTIST}${it.id}", it.name, null, grid = false, group = labels.artists))
            }
            ranked(results.albums, text) { it.name }.take(perSection).forEach {
                add(style.folder("${MediaTree.PREFIX_ALBUM}${it.id}", it.name, it.artworkHash, grid = false, group = labels.albums))
            }
            ranked(songs, text) { it.title }.take(perSection).forEach { add(style.track(it, group = labels.songs)) }
            ranked(shows, text) { it.title }.take(perSection).forEach {
                add(style.folder("${MediaTree.PREFIX_SHOW}${it.id}", it.title, it.artworkHash, grid = false, group = labels.podcasts))
            }
        }
    }

    /**
     * The ids to play for a voice [query], in order: the best artist's tracks, else the best
     * album's, else the best song, else the best show's latest episode. An empty query plays
     * Recently Played, or a shuffle of everything downloaded when nothing has been played.
     * Empty when nothing matches.
     */
    suspend fun resolveVoice(query: String): List<MediaId> = withContext(dispatchers.io) {
        val text = query.trim()
        if (text.isEmpty()) return@withContext emptyQueryIds()
        val results = searchDao.searchOnce(text, VOICE_FETCH)
        val artistTracks = ranked(results.artists, text) { it.name }.firstOrNull()
            ?.let { browseDao.artistTracksPage(it.id, MediaTree.CAR_LIST_CAP, 0) }.orEmpty()
        if (artistTracks.isNotEmpty()) return@withContext artistTracks.map(MediaId::of)
        val albumTracks = ranked(results.albums, text) { it.name }.firstOrNull()
            ?.let { browseDao.albumTracksPage(it.id, MediaTree.CAR_LIST_CAP, 0) }.orEmpty()
        if (albumTracks.isNotEmpty()) return@withContext albumTracks.map(MediaId::of)
        val song = ranked(results.tracks.filter { it.downloadState == DownloadState.Downloaded }, text) { it.title }.firstOrNull()
        if (song != null) return@withContext listOf(MediaId.of(song))
        val show = ranked(searchDao.searchShows(text, VOICE_FETCH), text) { it.title }.firstOrNull()
        show?.let { browseDao.episodesPage(it.id, 1, 0) }.orEmpty().map(MediaId::of)
    }

    private suspend fun emptyQueryIds(): List<MediaId> {
        val recent = browseDao.recentlyPlayedPage(MediaTree.RECENT_CAP, 0)
        if (recent.isNotEmpty()) return recent.map(MediaId::of)
        return browseDao.downloadedTrackIds().shuffled().map(MediaId::Track)
    }

    /** [rows] with exact name matches first, then prefix matches, then the rest, each tier in its original order. */
    private fun <T> ranked(rows: List<T>, query: String, name: (T) -> String): List<T> {
        val folded = LetterBuckets.fold(query)
        return rows.sortedBy { row ->
            val candidate = LetterBuckets.fold(name(row))
            when {
                candidate == folded -> TIER_EXACT
                candidate.startsWith(folded) -> TIER_PREFIX
                else -> TIER_OTHER
            }
        }
    }

    companion object {
        /** The most results per search section. */
        const val PER_SECTION = 10

        /** Rows read per section before ranking, so an exact match past the FTS order still surfaces. */
        private const val OVERFETCH = 5
        private const val VOICE_FETCH = 20
        private const val TIER_EXACT = 0
        private const val TIER_PREFIX = 1
        private const val TIER_OTHER = 2

        /**
         * The spoken query when [items] is a voice request: Media3 turns "play X" into one item
         * with an empty media id, no Uri, and the query in its request metadata (an empty query
         * for "play Bòcan"). Null for an ordinary request that carries media ids.
         */
        fun voiceQuery(items: List<MediaItem>): String? = items.singleOrNull()
            ?.takeIf { it.mediaId.isEmpty() && it.requestMetadata.mediaUri == null }
            ?.let { it.requestMetadata.searchQuery.orEmpty() }
    }
}
