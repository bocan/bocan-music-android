package io.cloudcauldron.bocan.playback

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.daos.BrowseDao
import io.cloudcauldron.bocan.persistence.daos.PlayStatsDao
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.PlayStatsEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.playback.audio.EffectsChain
import io.cloudcauldron.bocan.playback.browse.BrowseLabels
import io.cloudcauldron.bocan.playback.browse.MediaTree
import io.cloudcauldron.bocan.playback.podcast.EpisodeProgressRecorder
import io.cloudcauldron.bocan.playback.queue.QueuePersistence
import io.cloudcauldron.bocan.playback.stats.PlayStatsRecorder
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

private class FakePlayStatsDao : PlayStatsDao {
    override fun observeStats(trackId: Long): Flow<PlayStatsEntity?> = emptyFlow()
    override suspend fun stats(trackId: Long): PlayStatsEntity? = null
    override suspend fun pruneOrphanedBefore(cutoff: Instant) = Unit
    override suspend fun upsert(stats: PlayStatsEntity) = Unit
}

@UnstableApi
class TestPlaybackApp :
    Application(),
    PlaybackHost {
    override val playbackComponents: PlaybackComponents by lazy {
        val dispatchers = CoroutineDispatchers()
        PlaybackComponents(
            playerFactory = PlayerFactory(this, EffectsChain(dispatchers)),
            mediaItemSource = object : MediaItemSource {
                override suspend fun resolve(ids: List<MediaId>): List<MediaItem> = emptyList()
            },
            statsRecorder = PlayStatsRecorder(FakePlayStatsDao(), dispatchers, NoopLog),
            episodeRecorder = EpisodeProgressRecorder(FakeEpisodeStateDao(), FakePodcastDao(), dispatchers, NoopLog),
            queuePersistence = QueuePersistence(cacheDir, dispatchers, NoopLog),
            mediaTree = MediaTree(EmptyBrowseDao, EMPTY_LABELS, { null }, dispatchers),
            episodeSkipButtons = emptyList(),
            dispatchers = dispatchers
        )
    }

    private companion object {
        val EMPTY_LABELS = BrowseLabels("Continue", "Playlists", "Albums", "Artists", "Podcasts", "Songs")
    }
}

/** A browse DAO with nothing in it, for the service-construction test. */
private object EmptyBrowseDao : BrowseDao {
    override suspend fun albumsPage(limit: Int, offset: Int) = emptyList<AlbumEntity>()
    override suspend fun artistsPage(limit: Int, offset: Int) = emptyList<ArtistEntity>()
    override suspend fun showsPage(limit: Int, offset: Int) = emptyList<PodcastEntity>()
    override suspend fun playlistsPage(limit: Int, offset: Int) = emptyList<PlaylistEntity>()
    override suspend fun recentSongsPage(limit: Int, offset: Int) = emptyList<TrackEntity>()
    override suspend fun albumTracksPage(albumId: Long, limit: Int, offset: Int) = emptyList<TrackEntity>()
    override suspend fun artistTracksPage(artistId: Long, limit: Int, offset: Int) = emptyList<TrackEntity>()
    override suspend fun playlistTracksPage(playlistId: Long, limit: Int, offset: Int) = emptyList<TrackEntity>()
    override suspend fun episodesPage(podcastId: Long, limit: Int, offset: Int) = emptyList<EpisodeEntity>()
    override suspend fun continueListeningPage(limit: Int, offset: Int) = emptyList<EpisodeEntity>()
}

@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(application = TestPlaybackApp::class, sdk = [36])
class PlaybackServiceTests {
    @Test
    fun `the service starts and builds its session`() {
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        assertNotNull(controller.get())
        controller.destroy()
    }

    @Test
    fun `the player prepared with a silent source reaches ready`() {
        val context = ApplicationProvider.getApplicationContext<TestPlaybackApp>()
        val player = PlayerFactory(context, EffectsChain(CoroutineDispatchers())).create()
        try {
            player.setMediaSource(SilenceMediaSource(SILENCE_DURATION_US))
            player.prepare()
            TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
            assertEquals(Player.STATE_READY, player.playbackState)
        } finally {
            player.release()
        }
    }

    private companion object {
        const val SILENCE_DURATION_US = 5_000_000L
    }
}
