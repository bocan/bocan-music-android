package io.cloudcauldron.bocan.playback.browse

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import io.cloudcauldron.bocan.persistence.daos.BrowseDao
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaId
import kotlinx.coroutines.withContext

/** The human-readable, localized category titles the app supplies from strings.xml. */
data class BrowseLabels(
    val continueListening: String,
    val playlists: String,
    val albums: String,
    val artists: String,
    val podcasts: String,
    val songs: String
)

/**
 * The Android Auto (and Media3 browser) content tree, served from Room by index. The
 * shape is root -> [Continue Listening, Playlists, Albums, Artists, Podcasts, Songs],
 * each category paging to its items, and the album/artist/playlist/show folders paging to
 * playable tracks or episodes. Depth is capped at three and every level is a paged
 * LIMIT/OFFSET query, so Auto never waits on an on-demand tree walk (phase 10 gotcha).
 *
 * Browse items carry only a media id, title, and artwork Uri; when the user plays one the
 * controller sends the id back and the session resolves the real playable item. Category
 * titles are passed in as [BrowseLabels] so they stay localized in the app's resources.
 */
@UnstableApi
class MediaTree(
    private val browseDao: BrowseDao,
    private val labels: BrowseLabels,
    private val artworkUri: (String?) -> Uri?,
    private val dispatchers: CoroutineDispatchers
) {
    /** The browse root. Auto and the phone share one tree; [forAuto] is reserved for a shallower variant. */
    fun rootItem(): MediaItem = browsableFolder(ROOT_ID, title = "")

    /** The category folders under the root, in display order. */
    fun rootCategories(): List<MediaItem> = listOf(
        browsableFolder(CATEGORY_CONTINUE, labels.continueListening),
        browsableFolder(CATEGORY_PLAYLISTS, labels.playlists),
        browsableFolder(CATEGORY_ALBUMS, labels.albums),
        browsableFolder(CATEGORY_ARTISTS, labels.artists),
        browsableFolder(CATEGORY_PODCASTS, labels.podcasts),
        browsableFolder(CATEGORY_SONGS, labels.songs)
    )

    /** The children of [parentId], paged. An unknown id yields an empty list, never an error. */
    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> = withContext(dispatchers.io) {
        val limit = pageSize.coerceIn(1, MAX_PAGE)
        val offset = page.coerceAtLeast(0) * limit
        when {
            parentId == ROOT_ID -> if (page == 0) rootCategories() else emptyList()
            parentId == CATEGORY_CONTINUE -> browseDao.continueListeningPage(limit, offset).map(::episodeItem)
            parentId == CATEGORY_SONGS -> browseDao.recentSongsPage(limit, offset).map(::trackItem)
            parentId == CATEGORY_ALBUMS ->
                browseDao.albumsPage(limit, offset).map { browsableFolder("$PREFIX_ALBUM${it.id}", it.name, it.artworkHash) }
            parentId == CATEGORY_ARTISTS ->
                browseDao.artistsPage(limit, offset).map { browsableFolder("$PREFIX_ARTIST${it.id}", it.name) }
            parentId == CATEGORY_PLAYLISTS ->
                browseDao.playlistsPage(limit, offset).map { browsableFolder("$PREFIX_PLAYLIST${it.id}", it.name) }
            parentId == CATEGORY_PODCASTS ->
                browseDao.showsPage(limit, offset).map { browsableFolder("$PREFIX_SHOW${it.id}", it.title, it.artworkHash) }
            parentId.startsWith(PREFIX_ALBUM) -> browseDao.albumTracksPage(idOf(parentId, PREFIX_ALBUM), limit, offset).map(::trackItem)
            parentId.startsWith(PREFIX_ARTIST) -> browseDao.artistTracksPage(idOf(parentId, PREFIX_ARTIST), limit, offset).map(::trackItem)
            parentId.startsWith(PREFIX_PLAYLIST) ->
                browseDao.playlistTracksPage(idOf(parentId, PREFIX_PLAYLIST), limit, offset).map(::trackItem)
            parentId.startsWith(PREFIX_SHOW) -> browseDao.episodesPage(idOf(parentId, PREFIX_SHOW), limit, offset).map(::episodeItem)
            else -> emptyList()
        }
    }

    private fun trackItem(track: TrackEntity): MediaItem = playableItem(
        mediaId = MediaId.Track(track.id).raw,
        title = track.title,
        subtitle = track.artistName,
        artworkHash = track.artworkHash,
        durationMs = track.durationMs,
        isPodcast = false
    )

    private fun episodeItem(episode: EpisodeEntity): MediaItem = playableItem(
        mediaId = MediaId.Episode(episode.id).raw,
        title = episode.title,
        subtitle = null,
        artworkHash = null,
        durationMs = episode.durationMs,
        isPodcast = true
    )

    private fun browsableFolder(mediaId: String, title: String, artworkHash: String? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .apply { artworkUri(artworkHash)?.let(::setArtworkUri) }
            .build()
        return MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata).build()
    }

    @Suppress("LongParameterList") // the fields a browse row needs to render, not a smell
    private fun playableItem(
        mediaId: String,
        title: String,
        subtitle: String?,
        artworkHash: String?,
        durationMs: Long,
        isPodcast: Boolean
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setDurationMs(durationMs)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(if (isPodcast) MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE else MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { artworkUri(artworkHash)?.let(::setArtworkUri) }
            .build()
        return MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata).build()
    }

    private fun idOf(nodeId: String, prefix: String): Long = nodeId.removePrefix(prefix).toLongOrNull() ?: -1L

    companion object {
        const val ROOT_ID = "root"
        const val CATEGORY_CONTINUE = "bocan/continue"
        const val CATEGORY_PLAYLISTS = "bocan/playlists"
        const val CATEGORY_ALBUMS = "bocan/albums"
        const val CATEGORY_ARTISTS = "bocan/artists"
        const val CATEGORY_PODCASTS = "bocan/podcasts"
        const val CATEGORY_SONGS = "bocan/songs"

        private const val PREFIX_ALBUM = "bocan/album/"
        private const val PREFIX_ARTIST = "bocan/artist/"
        private const val PREFIX_PLAYLIST = "bocan/playlist/"
        private const val PREFIX_SHOW = "bocan/show/"
        private const val MAX_PAGE = 200
    }
}
