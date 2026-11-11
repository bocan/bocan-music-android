package io.cloudcauldron.bocan.app

import io.cloudcauldron.bocan.app.audits.DestinationCoverageTest
import io.cloudcauldron.bocan.app.audits.TagCoverageAudit
import io.cloudcauldron.bocan.app.audits.TagCoverageAuditEmpty
import io.cloudcauldron.bocan.app.audits.TagCoverageAuditFirstRun
import io.cloudcauldron.bocan.app.journeys.FirstRunJourney
import io.cloudcauldron.bocan.app.journeys.LibraryPlayJourney
import io.cloudcauldron.bocan.app.journeys.PodcastResumeJourney
import org.junit.runner.RunWith
import org.junit.runners.Suite

/** Everything `make end-to-end` runs. A new journey, audit, or surface crawl joins this list. */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    FirstRunJourney::class,
    LibraryPlayJourney::class,
    PodcastResumeJourney::class,
    DestinationCoverageTest::class,
    TagCoverageAudit::class,
    TagCoverageAuditFirstRun::class,
    TagCoverageAuditEmpty::class
)
class E2ESuite
