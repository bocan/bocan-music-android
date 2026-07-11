package io.cloudcauldron.bocan.playback.stats

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class PlayStatsRulesTests {
    @Test
    fun `a 29 second track fully played is ineligible`() {
        assertEquals(PlayEvent.None, PlayStatsRules.classify(durationMs = 29_000, reachedMs = 29_000))
    }

    @Test
    fun `a ten minute track played four minutes counts as a play`() {
        // 4 minutes is below 50 percent (5 minutes) but hits the absolute threshold.
        assertEquals(PlayEvent.Play, PlayStatsRules.classify(durationMs = 600_000, reachedMs = 240_000))
    }

    @Test
    fun `reaching exactly fifty percent counts as a play`() {
        assertEquals(PlayEvent.Play, PlayStatsRules.classify(durationMs = 100_000, reachedMs = 50_000))
    }

    @Test
    fun `a skip at forty percent records the skip position`() {
        val event = PlayStatsRules.classify(durationMs = 200_000, reachedMs = 80_000)
        assertEquals(PlayEvent.Skip(afterSeconds = 80), event)
    }

    @Test
    fun `a thirty second track played through counts as a play`() {
        assertEquals(PlayEvent.Play, PlayStatsRules.classify(durationMs = 30_000, reachedMs = 30_000))
    }

    @Test
    fun `reach beyond duration is clamped and still a play`() {
        assertEquals(PlayEvent.Play, PlayStatsRules.classify(durationMs = 200_000, reachedMs = 500_000))
    }

    @Test
    fun `the play threshold is the smaller of half and four minutes`() {
        assertEquals(50_000, PlayStatsRules.playThresholdMs(100_000))
        assertEquals(PlayStatsRules.PLAY_ABSOLUTE_MS, PlayStatsRules.playThresholdMs(3_600_000))
    }

    @Test
    fun `a barely eligible track skipped early is a zero second skip`() {
        val event = PlayStatsRules.classify(durationMs = 40_000, reachedMs = 500)
        assertTrue(event is PlayEvent.Skip)
        assertEquals(0, (event as PlayEvent.Skip).afterSeconds)
    }
}
