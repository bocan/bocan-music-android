package io.cloudcauldron.bocan.playback.stats

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ResumePolicyTests {
    @Test
    fun `items over twenty minutes resume`() {
        assertTrue(ResumePolicy.shouldResume(21L * 60 * 1000))
    }

    @Test
    fun `ordinary music restarts`() {
        assertFalse(ResumePolicy.shouldResume(4L * 60 * 1000))
    }

    @Test
    fun `exactly twenty minutes restarts`() {
        assertFalse(ResumePolicy.shouldResume(ResumePolicy.RESUME_THRESHOLD_MS))
    }
}
