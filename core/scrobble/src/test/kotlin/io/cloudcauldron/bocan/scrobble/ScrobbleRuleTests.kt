package io.cloudcauldron.bocan.scrobble

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** The eligibility rules table from the phase, mirroring the Mac. */
class ScrobbleRuleTests {
    @Test
    fun `a 29 second track played in full is ineligible`() {
        assertFalse(ScrobbleRule.isEligible(durationSec = 29, playedSec = 29, isPodcast = false))
    }

    @Test
    fun `a 10 minute track at 4 minutes is eligible`() {
        assertTrue(ScrobbleRule.isEligible(durationSec = 600, playedSec = 240, isPodcast = false))
    }

    @Test
    fun `a 6 minute track at 50 percent is eligible`() {
        assertTrue(ScrobbleRule.isEligible(durationSec = 360, playedSec = 180, isPodcast = false))
    }

    @Test
    fun `a 6 minute track just short of half is ineligible`() {
        assertFalse(ScrobbleRule.isEligible(durationSec = 360, playedSec = 179, isPodcast = false))
    }

    @Test
    fun `a podcast is never eligible however long it played`() {
        assertFalse(ScrobbleRule.isEligible(durationSec = 3_600, playedSec = 3_600, isPodcast = true))
    }

    @Test
    fun `the threshold is the lesser of half the track and four minutes`() {
        assertEquals(90, ScrobbleRule.thresholdSec(180))
        assertEquals(240, ScrobbleRule.thresholdSec(1_200))
    }
}
