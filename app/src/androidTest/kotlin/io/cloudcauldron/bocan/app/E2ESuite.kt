package io.cloudcauldron.bocan.app

import io.cloudcauldron.bocan.app.journeys.FirstRunJourney
import io.cloudcauldron.bocan.app.journeys.LibraryPlayJourney
import io.cloudcauldron.bocan.app.journeys.PodcastResumeJourney
import org.junit.runner.RunWith
import org.junit.runners.Suite

/** Everything `make end-to-end` runs. A new journey or surface crawl joins this list. */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    FirstRunJourney::class,
    LibraryPlayJourney::class,
    PodcastResumeJourney::class
)
class E2ESuite
