package io.cloudcauldron.bocan.app

import io.cloudcauldron.bocan.app.audits.DestinationCoverageTest
import io.cloudcauldron.bocan.app.audits.TagCoverageAudit
import io.cloudcauldron.bocan.app.audits.TagCoverageAuditEmpty
import io.cloudcauldron.bocan.app.audits.TagCoverageAuditFirstRun
import org.junit.runner.RunWith
import org.junit.runners.Suite

/** The tag audits alone (`make end-to-end-audit`); `E2ESuite` runs them too. */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    DestinationCoverageTest::class,
    TagCoverageAudit::class,
    TagCoverageAuditFirstRun::class,
    TagCoverageAuditEmpty::class
)
class AuditSuite
