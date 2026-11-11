package io.cloudcauldron.bocan.app.audits

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import io.cloudcauldron.bocan.app.E2ETest
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.navigation.Destination
import kotlin.reflect.KClass
import kotlin.test.assertTrue

/**
 * The base of the tag audits: a crawl that walks screens and sheets through their tagged
 * controls and runs [TagAudit.check] at each stop. Walking by tag means the crawl itself also
 * proves the tags it uses exist. Each audit names the [Destination] routes it visits, so
 * `DestinationCoverageTest` can prove that, between them, the audits reach every route.
 */
abstract class AuditTest(profile: FixtureProfile, completeOnboarding: Boolean = true, keepInstall: Boolean = false) :
    E2ETest(profile, completeOnboarding, keepInstall) {
    protected val audit: TagAudit by lazy { TagAudit(composeRule) }
    private val visited = mutableSetOf<KClass<out Destination>>()

    /** Audit the screen showing now; [route] is the navigation route it belongs to, when it is one. */
    protected fun check(screen: String, route: KClass<out Destination>? = null) {
        route?.let { visited += it }
        audit.check(screen)
    }

    /** Fail on any untagged control seen, then on any of [routes] the crawl did not reach. */
    protected fun finish(routes: Set<KClass<out Destination>>) {
        audit.assertNoGaps()
        val missed = routes - visited
        assertTrue(missed.isEmpty(), "the crawl never reached: ${missed.map { it.simpleName }}")
    }

    protected fun tap(tag: String) = ui.tap(tag)

    /** Tap a control inside a scrolling column, bringing it on screen first. */
    protected fun tapScrolled(tag: String) {
        ui.awaitTag(tag).performScrollTo().performClick()
    }

    protected fun tapRow(tag: String, label: String) {
        ui.awaitRow(tag, label).performClick()
    }

    /** System back: closes a sheet, menu, or dialog, or pops a pushed screen. */
    protected fun back() {
        composeRule.waitForIdle()
        Espresso.pressBack()
    }
}
