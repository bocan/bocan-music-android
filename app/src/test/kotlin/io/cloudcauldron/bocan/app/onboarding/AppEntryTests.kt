package io.cloudcauldron.bocan.app.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class AppEntryTests {
    @Test
    fun `fresh unpaired install lands in onboarding`() {
        assertEquals(AppEntry.Onboarding, resolveEntry(onboardingCompleted = false, paired = false))
    }

    @Test
    fun `paired install goes straight to the library even without the flag`() {
        assertEquals(AppEntry.Home, resolveEntry(onboardingCompleted = false, paired = true))
    }

    @Test
    fun `a skipped flow never re-triggers`() {
        assertEquals(AppEntry.Home, resolveEntry(onboardingCompleted = true, paired = false))
        assertEquals(AppEntry.Home, resolveEntry(onboardingCompleted = true, paired = true))
    }
}
