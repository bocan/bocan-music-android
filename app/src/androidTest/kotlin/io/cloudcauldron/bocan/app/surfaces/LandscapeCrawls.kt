package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import org.junit.Before
import org.junit.Test

/**
 * Every screen once in landscape (phase 17, slice 4): each crawl table runs again, whole, on
 * the landscape layout (the navigation rail in place of the bottom bar, Now Playing side by
 * side). The tables need no landscape edits: the rail's items carry the bottom bar's tags, and
 * the runner brings each control on screen before it touches it. Every step is audited, so a
 * control only landscape shows still needs a tag.
 */
class LandscapeCrawl : CrawlTest(FixtureProfile.PairedLibrary) {
    @Before
    fun landscape() = rotateToLandscape()

    @Test
    fun library() = run("library", LibraryCrawl.table(ctx))

    @Test
    fun detailScreens() = run("detail screens", DetailCrawl.table(ctx))

    @Test
    fun search() = run("search", SearchCrawl.table(ctx))

    @Test
    fun podcasts() = run("podcasts", PodcastsCrawl.table(ctx))

    @Test
    fun podcastPlayer() = run("podcast player", PodcastPlayerCrawl.table(ctx))

    @Test
    fun nowPlaying() = run("now playing", NowPlayingCrawl.table(ctx))

    @Test
    fun settings() = run("settings", SettingsCrawl.table(ctx))

    @Test
    fun equalizer() = run("equalizer", EqualizerCrawl.table(ctx))

    @Test
    fun sync() = run("sync", SyncCrawl.table(ctx))

    private fun run(name: String, table: List<Step>) {
        crawl("$name (landscape)", table)
        finishCrawl()
    }
}

/** The welcome flow and the unpaired sync screen in landscape. */
class LandscapeFirstRunCrawl : CrawlTest(FixtureProfile.UnpairedDemo, completeOnboarding = false) {
    @Before
    fun landscape() = rotateToLandscape()

    @Test
    fun firstRun() {
        crawl("first run (landscape)", FirstRunCrawl.table(ctx))
        finishCrawl()
    }
}

/** The empty states in landscape. */
class LandscapeEmptyLibraryCrawl : CrawlTest(FixtureProfile.EmptyPaired) {
    @Before
    fun landscape() = rotateToLandscape()

    @Test
    fun emptyLibrary() {
        crawl("empty library (landscape)", EmptyLibraryCrawl.table(ctx))
        finishCrawl()
    }
}
