package io.cloudcauldron.bocan.app

import io.cloudcauldron.bocan.app.journeys.LibraryPlayJourney
import org.junit.runner.RunWith
import org.junit.runners.Suite

/** The quick subset `make end-to-end-smoke` runs: one journey through the fixture library. */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    LibraryPlayJourney::class
)
class SmokeSuite
