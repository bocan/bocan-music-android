package io.cloudcauldron.bocan.playback.browse

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.persistence.daos.BrowseDao
import io.cloudcauldron.bocan.persistence.model.NameRow
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import kotlinx.coroutines.withContext

/** The human-readable, localized titles the app supplies from strings.xml. */
data class BrowseLabels(
    val home: String,
    val recentlyPlayed: String,
    val continueListening: String,
    val playlists: String,
    val albums: String,
    val artists: String,
    val podcasts: String,
    val songs: String,
    val digitsBucket: String,
    val otherBucket: String
)

/**
 * The Android Auto (and Media3 browser) content tree, served from Room (phases 10 and 15).
 *
 * Shape, built for a driver: the root is four tabs, Home, Playlists, Albums, Artists
 * (three when the car allows fewer). Home holds Recently Played, Continue Listening,
 * Podcasts, and Songs. Albums and Artists open to letter buckets ([LetterBuckets]), each
 * bucket to its members, so no list is a flat wall of every name. Albums and shows render
 * as grids of covers through [BrowseStyle].
 *
 * Paging: a paging browser's explicit page size is honoured up to [MAX_EXPLICIT_PAGE]. Android
 * Auto asks once with no page options, which Media3 delivers as page 0 of size
 * `Int.MAX_VALUE`; that request is served whole, up to [CAR_LIST_CAP]. A node bigger than the
 * cap is a design bug in the tree shape, so it is logged, never hidden. Recently Played and
 * Songs are bounded by definition (the newest 50 plays, the newest [CAR_LIST_CAP] synced).
 *
 * Browse items carry only a media id; when one is played the session resolves the real
 * item. Ids embed stable keys (an id or a bucket letter), never a row offset.
 */
@UnstableApi
class MediaTree(
    private val browseDao: BrowseDao,
    private val labels: BrowseLabels,
    private val style: BrowseStyle,
    private val dispatchers: CoroutineDispatchers,
    private val log: AppLog
) {
    /** The browse root. */
    fun rootItem(): MediaItem = style.folder(ROOT_ID, title = "", artworkHash = null, grid = false)

    /** The root tabs in display order; three when the car reports a root children limit under four. */
    fun rootCategories(rootChildrenLimit: Int?): List<MediaItem> {
        val tabs = listOf(
            style.folder(CATEGORY_HOME, labels.home, null, grid = false),
            style.folder(CATEGORY_PLAYLISTS, labels.playlists, null, grid = false),
            style.folder(CATEGORY_ALBUMS, labels.albums, null, grid = false),
            style.folder(CATEGORY_ARTISTS, labels.artists, null, grid = false)
        )
        return if (rootChildrenLimit != null && rootChildrenLimit < tabs.size) tabs.take(COLLAPSED_TABS) else tabs
    }

    /**
     * The children of [parentId] for one page. [rootChildrenLimit] is the car's root hint and
     * only shapes the root. An unknown id or an invalid page yields an empty list, never an error.
     */
    suspend fun children(parentId: String, page: Int, pageSize: Int, rootChildrenLimit: Int? = null): List<MediaItem> =
        withContext(dispatchers.io) {
            val window = Window.of(page, pageSize) ?: return@withContext emptyList()
            when {
                parentId == ROOT_ID -> serveList(parentId, window, rootCategories(rootChildrenLimit))
                parentId == CATEGORY_HOME -> serveList(parentId, window, homeFolders())
                parentId == CATEGORY_RECENT ->
                    serve(parentId, window, nodeCap = RECENT_CAP) { limit, offset ->
                        browseDao.recentlyPlayedPage(limit, offset).map { style.track(it) }
                    }
                parentId == CATEGORY_CONTINUE ->
                    serve(parentId, window) { limit, offset -> browseDao.continueListeningPage(limit, offset).map { style.episode(it) } }
                parentId == CATEGORY_PODCASTS ->
                    serve(parentId, window) { limit, offset ->
                        browseDao.showsPage(limit, offset).map { show ->
                            style.folder("$PREFIX_SHOW${show.id}", show.title, show.artworkHash, grid = false)
                        }
                    }
                parentId == CATEGORY_SONGS ->
                    serve(parentId, window, nodeCap = CAR_LIST_CAP) { limit, offset ->
                        browseDao.recentSongsPage(limit, offset).map { style.track(it) }
                    }
                parentId == CATEGORY_PLAYLISTS ->
                    serve(parentId, window) { limit, offset ->
                        browseDao.playlistsPage(limit, offset).map { style.folder("$PREFIX_PLAYLIST${it.id}", it.name, null, grid = false) }
                    }
                parentId == CATEGORY_ALBUMS ->
                    serveList(parentId, window, bucketFolders(PREFIX_ALBUM_BUCKET, browseDao.albumNames(), grid = true))
                parentId == CATEGORY_ARTISTS ->
                    serveList(parentId, window, bucketFolders(PREFIX_ARTIST_BUCKET, browseDao.artistNames(), grid = false))
                parentId.startsWith(PREFIX_ALBUM_BUCKET) ->
                    bucket(parentId, window, PREFIX_ALBUM_BUCKET, browseDao.albumNames()) { ids ->
                        browseDao.albumsByIds(ids).map { album ->
                            album.id to style.folder("$PREFIX_ALBUM${album.id}", album.name, album.artworkHash, grid = false)
                        }
                    }
                parentId.startsWith(PREFIX_ARTIST_BUCKET) ->
                    bucket(parentId, window, PREFIX_ARTIST_BUCKET, browseDao.artistNames()) { ids ->
                        browseDao.artistsByIds(ids).map { it.id to style.folder("$PREFIX_ARTIST${it.id}", it.name, null, grid = false) }
                    }
                parentId.startsWith(PREFIX_ALBUM) ->
                    serve(parentId, window) { limit, offset ->
                        browseDao.albumTracksPage(idOf(parentId, PREFIX_ALBUM), limit, offset).map { style.track(it) }
                    }
                parentId.startsWith(PREFIX_ARTIST) ->
                    serve(parentId, window) { limit, offset ->
                        browseDao.artistTracksPage(idOf(parentId, PREFIX_ARTIST), limit, offset).map { style.track(it) }
                    }
                parentId.startsWith(PREFIX_PLAYLIST) ->
                    serve(parentId, window) { limit, offset ->
                        browseDao.playlistTracksPage(idOf(parentId, PREFIX_PLAYLIST), limit, offset).map { style.track(it) }
                    }
                parentId.startsWith(PREFIX_SHOW) ->
                    serve(parentId, window) { limit, offset ->
                        browseDao.episodesPage(idOf(parentId, PREFIX_SHOW), limit, offset).map { style.episode(it) }
                    }
                else -> emptyList()
            }
        }

    private fun homeFolders(): List<MediaItem> = listOf(
        style.folder(CATEGORY_RECENT, labels.recentlyPlayed, null, grid = false),
        style.folder(CATEGORY_CONTINUE, labels.continueListening, null, grid = false),
        style.folder(CATEGORY_PODCASTS, labels.podcasts, null, grid = true),
        style.folder(CATEGORY_SONGS, labels.songs, null, grid = false)
    )

    /** One folder per non-empty bucket of [names], titled by its key; [grid] styles each bucket's members. */
    private fun bucketFolders(prefix: String, names: List<NameRow>, grid: Boolean): List<MediaItem> =
        LetterBuckets.group(names, NameRow::name).keys.map { key ->
            val title = when (key) {
                LetterBuckets.DIGITS -> labels.digitsBucket
                LetterBuckets.OTHER -> labels.otherBucket
                else -> key
            }
            style.folder("$prefix$key", title, null, grid = grid)
        }

    /**
     * One window of the bucket named by [nodeId], in bucket order. [readByIds] reads one page of
     * members by id and returns (id, item) pairs in any order; an unknown bucket has no members.
     */
    private suspend fun bucket(
        nodeId: String,
        window: Window,
        prefix: String,
        names: List<NameRow>,
        readByIds: suspend (List<Long>) -> List<Pair<Long, MediaItem>>
    ): List<MediaItem> {
        val members = LetterBuckets.group(names, NameRow::name)[nodeId.removePrefix(prefix)].orEmpty()
        return serve(nodeId, window) { limit, offset ->
            val ids = members.drop(offset).take(limit).map(NameRow::id)
            val byId = readByIds(ids).toMap()
            ids.mapNotNull(byId::get)
        }
    }

    private suspend fun serveList(nodeId: String, window: Window, items: List<MediaItem>): List<MediaItem> =
        serve(nodeId, window) { limit, offset -> items.drop(offset).take(limit) }

    /**
     * Serve one window of a node through [fetch]. [nodeCap] bounds a node by definition
     * (no warning); otherwise an unbounded request reads one row past [CAR_LIST_CAP] so an
     * oversized node is logged with its id before it is cut to the cap.
     */
    private suspend fun serve(
        nodeId: String,
        window: Window,
        nodeCap: Int? = null,
        fetch: suspend (limit: Int, offset: Int) -> List<MediaItem>
    ): List<MediaItem> = when (window) {
        is Window.Paged -> {
            val room = nodeCap?.let { it - window.offset } ?: window.limit
            if (room <= 0) emptyList() else fetch(minOf(window.limit, room), window.offset)
        }
        Window.Unbounded -> if (nodeCap != null) {
            fetch(nodeCap, 0)
        } else {
            val rows = fetch(CAR_LIST_CAP + 1, 0)
            if (rows.size > CAR_LIST_CAP) {
                log.warning("browse.node.overCap", mapOf("node" to nodeId, "sizeAtLeast" to rows.size, "cap" to CAR_LIST_CAP))
            }
            rows.take(CAR_LIST_CAP)
        }
    }

    private fun idOf(nodeId: String, prefix: String): Long = nodeId.removePrefix(prefix).toLongOrNull() ?: -1L

    /** One request's slice of a node: an explicit page, or the whole node for a non-paging browser. */
    private sealed interface Window {
        data class Paged(val limit: Int, val offset: Int) : Window

        data object Unbounded : Window

        companion object {
            /** Null when the request can hold nothing (a negative page, an empty page, or a later page of an unbounded read). */
            fun of(page: Int, pageSize: Int): Window? = when {
                page < 0 || pageSize < 1 -> null
                pageSize == Int.MAX_VALUE -> if (page == 0) Unbounded else null
                else -> {
                    val limit = minOf(pageSize, MAX_EXPLICIT_PAGE)
                    Paged(limit, (page.toLong() * limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                }
            }
        }
    }

    companion object {
        /** The most rows one car list may hold; a node that reaches it is the wrong shape. */
        const val CAR_LIST_CAP = 400

        /** The largest explicit page a paging browser is given. */
        const val MAX_EXPLICIT_PAGE = 500

        /** Recently Played holds the newest 50 plays. */
        const val RECENT_CAP = 50

        const val ROOT_ID = "root"
        const val CATEGORY_HOME = "bocan/home"
        const val CATEGORY_RECENT = "bocan/recent"
        const val CATEGORY_CONTINUE = "bocan/continue"
        const val CATEGORY_PLAYLISTS = "bocan/playlists"
        const val CATEGORY_ALBUMS = "bocan/albums"
        const val CATEGORY_ARTISTS = "bocan/artists"
        const val CATEGORY_PODCASTS = "bocan/podcasts"
        const val CATEGORY_SONGS = "bocan/songs"

        /** A letter bucket of artists: this prefix plus the bucket key ("S", "0-9", "#"). */
        const val PREFIX_ARTIST_BUCKET = "bocan/artists/"

        /** A letter bucket of albums: this prefix plus the bucket key. */
        const val PREFIX_ALBUM_BUCKET = "bocan/albums/"

        internal const val PREFIX_ALBUM = "bocan/album/"
        internal const val PREFIX_ARTIST = "bocan/artist/"
        internal const val PREFIX_PLAYLIST = "bocan/playlist/"
        internal const val PREFIX_SHOW = "bocan/show/"

        private const val COLLAPSED_TABS = 3

        /** The car's root children limit from the root request's extras; null when absent or not positive. */
        fun rootChildrenLimit(extras: Bundle?): Int? = extras?.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 0)?.takeIf { it > 0 }
    }
}
