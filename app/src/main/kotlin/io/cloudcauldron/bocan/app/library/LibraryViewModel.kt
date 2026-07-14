package io.cloudcauldron.bocan.app.library

import io.cloudcauldron.bocan.app.data.LibraryPreferencesSource
import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.persistence.daos.PlaylistDao
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.model.AlbumSort
import io.cloudcauldron.bocan.persistence.model.TrackSort
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.engine.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The coarse library state that decides between browsing and a first-run empty screen. */
sealed interface LibraryStatus {
    data object Loading : LibraryStatus
    data object NotPaired : LibraryStatus
    data object Empty : LibraryStatus
    data class Syncing(val filesDone: Int, val filesTotal: Int) : LibraryStatus
    data object Content : LibraryStatus
}

/**
 * Serves every library tab from the synced DB, honouring the persisted sort orders and
 * remembering the selected tab. Each tab exposes its own [StateFlow] built with
 * [SharingStarted.WhileSubscribed] so rotation does not restart the DB flows but
 * backgrounding stops them. No writes: this is a read-only browser.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    libraryDao: LibraryDao,
    playlistDao: PlaylistDao,
    syncServer: Flow<SyncServerEntity?>,
    syncState: StateFlow<SyncState>,
    private val prefs: LibraryPreferencesSource,
    dispatchers: CoroutineDispatchers
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val tab = MutableStateFlow(LibraryTab.Albums)
    val selectedTab: StateFlow<LibraryTab> = tab

    val albumSort: StateFlow<AlbumSort> = prefs.albumSort.share(AlbumSort.Name)
    val trackSort: StateFlow<TrackSort> = prefs.trackSort.share(TrackSort.Title)

    val albums: StateFlow<List<AlbumUi>> =
        prefs.albumSort.flatMapLatest { libraryDao.observeAlbums(it) }
            .map { albums -> albums.map { it.toUi() } }
            .share(emptyList())

    val songs: StateFlow<List<TrackUi>> =
        prefs.trackSort.flatMapLatest { libraryDao.observeAllTracks(it) }
            .map { tracks -> tracks.map { it.toUi() } }
            .share(emptyList())

    val artists: StateFlow<List<ArtistUi>> =
        combine(
            libraryDao.observeArtists(),
            libraryDao.observeAlbums(AlbumSort.Name),
            libraryDao.observeArtistTrackCounts()
        ) { artists, albums, songCounts ->
            val albumCounts = albums.groupingBy { it.albumArtistName }.eachCount()
            val songByArtist = songCounts.associate { it.artistId to it.count }
            artists.map { ArtistUi(it.id, it.name, albumCounts[it.name] ?: 0, songByArtist[it.id] ?: 0) }
        }.share(emptyList())

    val genres: StateFlow<List<String>> = libraryDao.observeGenres().share(emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = playlistDao.observePlaylistTree().share(emptyList())

    /** All tracks reduced to (id, relPath) for the in-memory folder tree; never derived in SQL. */
    val folderItems: StateFlow<List<FolderTree.Item>> =
        libraryDao.observeAllTracksByTitle()
            .map { tracks -> tracks.map { FolderTree.Item(it.id, it.relPath) } }
            .share(emptyList())

    val status: StateFlow<LibraryStatus> =
        combine(syncServer, libraryDao.observeDownloadCounts(), syncState) { server, counts, sync ->
            val total = counts.pending + counts.downloaded + counts.failed
            val syncing = sync is SyncState.CheckingManifest || sync is SyncState.Transferring || sync is SyncState.Applying
            when {
                server == null -> LibraryStatus.NotPaired
                total > 0 -> LibraryStatus.Content
                sync is SyncState.Transferring -> LibraryStatus.Syncing(sync.filesDone, sync.filesTotal)
                syncing -> LibraryStatus.Syncing(0, 0)
                else -> LibraryStatus.Empty
            }
        }.share(LibraryStatus.Loading)

    init {
        scope.launch { tab.value = prefs.lastTab.first() }
    }

    fun selectTab(next: LibraryTab) {
        tab.value = next
        scope.launch { prefs.setLastTab(next) }
    }

    fun setAlbumSort(sort: AlbumSort) = scope.launch { prefs.setAlbumSort(sort) }.let {}

    fun setTrackSort(sort: TrackSort) = scope.launch { prefs.setTrackSort(sort) }.let {}

    fun dispose() = scope.cancel()

    private fun <T> Flow<T>.share(initial: T): StateFlow<T> = stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), initial)

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
