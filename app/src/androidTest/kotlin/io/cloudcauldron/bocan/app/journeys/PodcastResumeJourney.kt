package io.cloudcauldron.bocan.app.journeys

import io.cloudcauldron.bocan.app.E2ETest
import io.cloudcauldron.bocan.app.MiniPlayer
import io.cloudcauldron.bocan.app.PodcastsScreen
import io.cloudcauldron.bocan.app.ShowScreen
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * The in-progress fixture episode resumes where the manifest left it (40 percent), plays on,
 * and after a pause its position is saved and survives the activity being recreated.
 */
class PodcastResumeJourney : E2ETest(FixtureProfile.PairedLibrary) {
    @Test
    fun anInProgressEpisodeResumesAndKeepsItsPlace() {
        val episode = fixture.episodes.single { it.title == EPISODE }
        val mediaId = episodeMediaId(EPISODE)

        val podcasts = PodcastsScreen(ui)
        podcasts.open()
        podcasts.openShow(SHOW)
        ShowScreen(ui).playEpisode(EPISODE)

        awaitPlayer("'$EPISODE' resumed at its saved ${episode.playPositionMs} ms") {
            it.current?.mediaId == mediaId && it.isPlaying && it.positionMs >= episode.playPositionMs
        }
        val resumedAt = player.positionMs
        assertTrue(resumedAt - episode.playPositionMs < RESUME_TOLERANCE_MS, "resumed at $resumedAt ms, not near ${episode.playPositionMs}")
        awaitPlayer("the episode playing on past $resumedAt ms") { it.positionMs > resumedAt }

        MiniPlayer(ui).playPause()
        awaitPlayer("paused from the mini player") { !it.isPlaying }
        val pausedAt = player.positionMs
        awaitState("the paused position ($pausedAt ms) saved for '$EPISODE'") {
            val saved = savedPosition(episode.id)
            saved != null && abs(saved - pausedAt) <= SAVE_TOLERANCE_MS
        }
        screenshot("paused")

        composeRule.activityRule.scenario.recreate()

        MiniPlayer(ui).awaitArtwork()
        assertEquals(mediaId, player.current?.mediaId, "recreating the activity lost the episode")
        assertFalse(player.isPlaying, "recreating the activity started playback")
        assertTrue(abs(player.positionMs - pausedAt) <= SAVE_TOLERANCE_MS, "position moved from $pausedAt to ${player.positionMs} ms")
        val saved = savedPosition(episode.id)
        assertTrue(saved != null && abs(saved - pausedAt) <= SAVE_TOLERANCE_MS, "saved position $saved ms, paused at $pausedAt ms")
    }

    private fun savedPosition(episodeId: String): Long? = runBlocking { graph.database.episodeStateDao().state(episodeId)?.playPositionMs }

    private companion object {
        const val SHOW = "Tidewater Talks"
        const val EPISODE = "Fixture Episode Two"
        const val RESUME_TOLERANCE_MS = 5_000L
        const val SAVE_TOLERANCE_MS = 1_000L
    }
}
