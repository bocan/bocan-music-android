package io.cloudcauldron.bocan.app

import io.cloudcauldron.bocan.app.gestures.NowPlayingGestureTests
import io.cloudcauldron.bocan.app.journeys.FirstRunJourney
import io.cloudcauldron.bocan.app.journeys.LibraryPlayJourney
import io.cloudcauldron.bocan.app.journeys.PodcastResumeJourney
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Everything `make end-to-end` runs: the journeys, the gesture tests, the tag audits
 * ([AuditSuite]), and the surface crawls ([CrawlSuite]). A new journey joins this list; a new
 * audit or crawl joins its own suite.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    FirstRunJourney::class,
    LibraryPlayJourney::class,
    PodcastResumeJourney::class,
    NowPlayingGestureTests::class,
    AuditSuite::class,
    CrawlSuite::class
)
class E2ESuite
