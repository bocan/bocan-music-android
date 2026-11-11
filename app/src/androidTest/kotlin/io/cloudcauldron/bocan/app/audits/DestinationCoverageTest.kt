package io.cloudcauldron.bocan.app.audits

import io.cloudcauldron.bocan.app.navigation.Destination
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Between them, the tag audits reach every navigation route in [Destination]. A new route
 * fails here until an audit crawls it. Each audit proves its own list when it runs
 * (`AuditTest.finish`); this test proves the lists add up to the whole app.
 */
class DestinationCoverageTest {
    @Test
    fun theAuditsReachEveryRoute() {
        val routes = Destination::class.java.classes
            .filter { Destination::class.java.isAssignableFrom(it) }
            .map { it.simpleName }
            .toSortedSet()
        val audited = (TagCoverageAudit.ROUTES + TagCoverageAuditFirstRun.ROUTES + TagCoverageAuditEmpty.ROUTES)
            .map { it.java.simpleName }
            .toSortedSet()
        assertEquals(routes, audited, "routes in Destination.kt versus routes the tag audits crawl")
    }
}
