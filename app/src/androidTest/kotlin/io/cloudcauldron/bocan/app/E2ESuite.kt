package io.cloudcauldron.bocan.app

import io.cloudcauldron.bocan.app.gestures.NowPlayingGestureTests
import io.cloudcauldron.bocan.app.journeys.FirstRunJourney
import io.cloudcauldron.bocan.app.journeys.LibraryPlayJourney
import io.cloudcauldron.bocan.app.journeys.PodcastResumeJourney
import io.cloudcauldron.bocan.app.sync.FakeMacVectorsTest
import io.cloudcauldron.bocan.app.sync.HermeticSyncJourney
import io.cloudcauldron.bocan.app.system.SystemSurfaceTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Everything `make end-to-end` runs: the journeys, the gesture tests, the widget and
 * notification checks, pairing and sync with the loopback fake Mac, the tag audits
 * ([AuditSuite]), and the surface crawls ([CrawlSuite]). A new journey joins this list; a new
 * audit or crawl joins its own suite.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    FirstRunJourney::class,
    LibraryPlayJourney::class,
    PodcastResumeJourney::class,
    NowPlayingGestureTests::class,
    SystemSurfaceTests::class,
    FakeMacVectorsTest::class,
    HermeticSyncJourney::class,
    AuditSuite::class,
    CrawlSuite::class
)
class E2ESuite
