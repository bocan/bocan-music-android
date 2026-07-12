package io.cloudcauldron.bocan.app.player

import io.cloudcauldron.bocan.app.FakeLibraryDao
import io.cloudcauldron.bocan.app.FakePlayStatsDao
import io.cloudcauldron.bocan.app.podcasts.FakeEpisodeStateDao
import io.cloudcauldron.bocan.app.podcasts.FakePodcastDao
import io.cloudcauldron.bocan.app.podcasts.episode
import io.cloudcauldron.bocan.app.podcasts.podcast
import io.cloudcauldron.bocan.app.trackEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeStateEntity
import io.cloudcauldron.bocan.persistence.entities.PlayStatsEntity
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.queue.NowPlayingItem
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import io.cloudcauldron.bocan.playback.session.AudioPipelineFormat
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongDetailsViewModelTests {
    private val library = FakeLibraryDao()
    private val podcasts = FakePodcastDao()
    private val episodeState = FakeEpisodeStateDao()
    private val playStats = FakePlayStatsDao()

    private fun viewModel(transport: FakePlaybackTransport): SongDetailsViewModel {
        val d = UnconfinedTestDispatcher()
        val dispatchers = CoroutineDispatchers(io = d, default = d, main = d)
        return SongDetailsViewModel(transport, library, podcasts, episodeState, playStats, dispatchers)
    }

    private fun playing(mediaId: String) = PlayerUiState(
        current = NowPlayingItem(mediaId = mediaId, title = "t", artist = null, album = null, artworkUri = null, durationMs = 0)
    )

    @Test
    fun `a track resolves its fields and derives a bitrate`() = runTest {
        library.tracksFlow.value = listOf(trackEntity(1))
        val transport = FakePlaybackTransport(playing("track:1"))
        val vm = viewModel(transport)
        val state = vm.state.first { it is SongDetailsUiState.Track } as SongDetailsUiState.Track
        assertEquals("Track 1", state.title)
        assertEquals("flac", state.format)
        assertTrue(state.lossless)
        // trackEntity has size 1000 bytes and 200_000 ms: 1000 * 8 / 200000 = 0 kbps floor.
        assertEquals(0L, state.bitrateKbps)
        vm.dispose()
    }

    @Test
    fun `a longer track over a small file derives a real bitrate`() = runTest {
        val big = trackEntity(2).copy(size = 5_000_000, durationMs = 200_000)
        library.tracksFlow.value = listOf(big)
        val transport = FakePlaybackTransport(playing("track:2"))
        val vm = viewModel(transport)
        val state = vm.state.first { it is SongDetailsUiState.Track } as SongDetailsUiState.Track
        assertEquals(5_000_000L * 8 / 200_000, state.bitrateKbps)
        vm.dispose()
    }

    @Test
    fun `bitrate is absent when the duration is unknown`() = runTest {
        library.tracksFlow.value = listOf(trackEntity(3).copy(durationMs = 0))
        val transport = FakePlaybackTransport(playing("track:3"))
        val vm = viewModel(transport)
        val state = vm.state.first { it is SongDetailsUiState.Track } as SongDetailsUiState.Track
        assertNull(state.bitrateKbps)
        vm.dispose()
    }

    @Test
    fun `play stats fold in when present and are absent otherwise`() = runTest {
        library.tracksFlow.value = listOf(trackEntity(4))
        playStats.stats[4] = PlayStatsEntity(trackId = 4, playCount = 7, lastPlayedAt = Instant.EPOCH)
        val transport = FakePlaybackTransport(playing("track:4"))
        val vm = viewModel(transport)
        val withStats = vm.state.first { it is SongDetailsUiState.Track } as SongDetailsUiState.Track
        assertEquals(7L, withStats.playCount)
        assertEquals(Instant.EPOCH, withStats.lastPlayedAt)
        vm.dispose()
    }

    @Test
    fun `a zero play count is omitted`() = runTest {
        library.tracksFlow.value = listOf(trackEntity(5))
        playStats.stats[5] = PlayStatsEntity(trackId = 5, playCount = 0)
        val transport = FakePlaybackTransport(playing("track:5"))
        val vm = viewModel(transport)
        val state = vm.state.first { it is SongDetailsUiState.Track } as SongDetailsUiState.Track
        assertNull(state.playCount)
        vm.dispose()
    }

    @Test
    fun `the pipeline line folds in after a refresh`() = runTest {
        library.tracksFlow.value = listOf(trackEntity(6))
        val transport = FakePlaybackTransport(playing("track:6"))
        transport.audioFormat = AudioPipelineFormat(sampleRateHz = 44_100, channelCount = 2, encoding = "audio/flac")
        val vm = viewModel(transport)
        vm.state.first { it is SongDetailsUiState.Track }
        vm.refreshPipeline()
        val state = vm.state.first { it is SongDetailsUiState.Track && it.pipeline != null } as SongDetailsUiState.Track
        assertEquals(44_100, state.pipeline?.sampleRateHz)
        vm.dispose()
    }

    @Test
    fun `an episode resolves its show and listening position`() = runTest {
        podcasts.shows.value = listOf(podcast(10, title = "The Show"))
        podcasts.episodes["ep9"] = episode("ep9", podcastId = 10, title = "Chapter Nine", durationMs = 1_800_000)
        episodeState.states["ep9"] = EpisodeStateEntity(episodeId = "ep9", playPositionMs = 60_000)
        val transport = FakePlaybackTransport(playing("episode:ep9"))
        val vm = viewModel(transport)
        val state = vm.state.first { it is SongDetailsUiState.Episode } as SongDetailsUiState.Episode
        assertEquals("Chapter Nine", state.title)
        assertEquals("The Show", state.show)
        assertEquals(60_000L, state.playPositionMs)
        assertEquals("MP3", state.format)
        vm.dispose()
    }

    @Test
    fun `nothing playing resolves to empty`() = runTest {
        val transport = FakePlaybackTransport(PlayerUiState())
        val vm = viewModel(transport)
        assertEquals(SongDetailsUiState.Empty, vm.state.first { it is SongDetailsUiState.Empty })
        vm.dispose()
    }
}
