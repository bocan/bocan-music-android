package io.cloudcauldron.bocan.app.library

import app.cash.turbine.test
import io.cloudcauldron.bocan.app.FakeLibraryDao
import io.cloudcauldron.bocan.app.FakeLibraryPreferences
import io.cloudcauldron.bocan.app.FakePlaylistDao
import io.cloudcauldron.bocan.app.albumEntity
import io.cloudcauldron.bocan.app.artistEntity
import io.cloudcauldron.bocan.app.syncServerEntity
import io.cloudcauldron.bocan.app.trackEntity
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.model.DownloadCounts
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.engine.SyncState
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTests {
    private val server = MutableStateFlow<SyncServerEntity?>(null)
    private val syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    private val libraryDao = FakeLibraryDao()
    private val playlistDao = FakePlaylistDao()
    private val prefs = FakeLibraryPreferences()

    private fun viewModel(): LibraryViewModel {
        val dispatcher = UnconfinedTestDispatcher()
        return LibraryViewModel(
            libraryDao = libraryDao,
            playlistDao = playlistDao,
            syncServer = server,
            syncState = syncState,
            prefs = prefs,
            dispatchers = CoroutineDispatchers(io = dispatcher, default = dispatcher)
        )
    }

    @Test
    fun `status is not paired when there is no server`() = runTest {
        viewModel().status.test {
            assertEquals(LibraryStatus.NotPaired, awaitItem())
        }
    }

    @Test
    fun `status is empty when paired with no tracks and idle`() = runTest {
        server.value = syncServerEntity()
        viewModel().status.test {
            assertEquals(LibraryStatus.Empty, awaitItem())
        }
    }

    @Test
    fun `status is syncing when paired, empty, and a transfer is running`() = runTest {
        server.value = syncServerEntity()
        syncState.value = SyncState.Transferring(2, 10, 0, 0, "song.flac")
        viewModel().status.test {
            assertEquals(LibraryStatus.Syncing(2, 10), awaitItem())
        }
    }

    @Test
    fun `status is content once tracks are present`() = runTest {
        server.value = syncServerEntity()
        libraryDao.countsFlow.value = DownloadCounts(pending = 0, downloaded = 42, failed = 0)
        viewModel().status.test {
            assertEquals(LibraryStatus.Content, awaitItem())
        }
    }

    @Test
    fun `artists carry derived album and song counts`() = runTest {
        libraryDao.artistsFlow.value = listOf(artistEntity(1, "ABBA"), artistEntity(2, "Rush"))
        libraryDao.albumsFlow.value = listOf(
            albumEntity(10, artist = "ABBA"),
            albumEntity(11, artist = "ABBA"),
            albumEntity(12, artist = "Rush")
        )
        libraryDao.tracksFlow.value = listOf(
            trackEntity(100, albumArtistId = 1),
            trackEntity(101, albumArtistId = 1),
            trackEntity(102, albumArtistId = 1),
            trackEntity(103, albumArtistId = 2)
        )
        viewModel().artists.test {
            val artists = awaitItem()
            val abba = artists.first { it.name == "ABBA" }
            val rush = artists.first { it.name == "Rush" }
            assertEquals(2, abba.albumCount)
            assertEquals(3, abba.songCount)
            assertEquals(1, rush.albumCount)
            assertEquals(1, rush.songCount)
        }
    }

    @Test
    fun `the selected tab is seeded from preferences and updates on selection`() = runTest {
        prefs.lastTab.value = LibraryTab.Songs
        val vm = viewModel()
        assertEquals(LibraryTab.Songs, vm.selectedTab.value)
        vm.selectTab(LibraryTab.Folders)
        assertEquals(LibraryTab.Folders, vm.selectedTab.value)
        assertEquals(LibraryTab.Folders, prefs.lastTab.value)
    }
}
