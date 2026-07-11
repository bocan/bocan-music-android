package io.cloudcauldron.bocan.playback.podcast

import io.cloudcauldron.bocan.persistence.entities.EpisodeStateEntity
import io.cloudcauldron.bocan.persistence.model.PlayState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class EpisodePlaybackRulesTests {
    private val duration = 3_600_000L // one hour

    private fun state(position: Long, playState: PlayState) =
        EpisodeStateEntity(episodeId = "ep", playPositionMs = position, playState = playState)

    @Test
    fun `an unplayed episode starts at zero`() {
        assertEquals(0, EpisodePlaybackRules.resumePosition(null, duration))
        assertEquals(0, EpisodePlaybackRules.resumePosition(state(0, PlayState.Unplayed), duration))
    }

    @Test
    fun `an in-progress episode below the floor starts at zero`() {
        assertEquals(0, EpisodePlaybackRules.resumePosition(state(3_000, PlayState.InProgress), duration))
    }

    @Test
    fun `an in-progress episode in the middle resumes to its position`() {
        assertEquals(1_800_000, EpisodePlaybackRules.resumePosition(state(1_800_000, PlayState.InProgress), duration))
    }

    @Test
    fun `an in-progress episode near the end restarts from zero`() {
        val nearEnd = duration - 10_000 // inside the 15 s end margin
        assertEquals(0, EpisodePlaybackRules.resumePosition(state(nearEnd, PlayState.InProgress), duration))
    }

    @Test
    fun `a played episode starts at zero`() {
        assertEquals(0, EpisodePlaybackRules.resumePosition(state(1_000_000, PlayState.Played), duration))
    }

    @Test
    fun `completion triggers within the end margin and not before`() {
        assertTrue(EpisodePlaybackRules.isCompleted(duration - 5_000, duration))
        assertTrue(EpisodePlaybackRules.isCompleted(duration, duration))
        assertFalse(EpisodePlaybackRules.isCompleted(duration - 20_000, duration))
        assertFalse(EpisodePlaybackRules.isCompleted(0, 0))
    }

    @Test
    fun `speed precedence is show override then show default then app default`() {
        assertEquals(1.5, EpisodePlaybackRules.effectiveSpeed(showOverride = 1.5, showDefault = 1.2, appDefault = 1.0))
        assertEquals(1.2, EpisodePlaybackRules.effectiveSpeed(showOverride = null, showDefault = 1.2, appDefault = 1.0))
        assertEquals(1.0, EpisodePlaybackRules.effectiveSpeed(showOverride = null, showDefault = null, appDefault = 1.0))
    }
}
