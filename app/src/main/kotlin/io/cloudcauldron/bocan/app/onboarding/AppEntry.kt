package io.cloudcauldron.bocan.app.onboarding

/** Where a launch lands: the first-run flow or the library. */
enum class AppEntry {
    /** Persisted state has not loaded yet; show nothing rather than flashing the wrong surface. */
    Undetermined,
    Onboarding,
    Home
}

/**
 * The onboarding state machine's one decision: a paired phone always goes
 * straight to the library (it was set up before the flag existed, or on another
 * launch), a finished or skipped flow never re-triggers, and only a fresh
 * unpaired install sees the welcome flow.
 */
fun resolveEntry(onboardingCompleted: Boolean, paired: Boolean): AppEntry = when {
    paired -> AppEntry.Home
    onboardingCompleted -> AppEntry.Home
    else -> AppEntry.Onboarding
}
