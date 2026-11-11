package io.cloudcauldron.bocan.app.surfaces

import android.app.Activity
import android.app.Instrumentation
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import io.cloudcauldron.bocan.app.audits.AuditTest
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before

/**
 * The base of every surface crawl: runs crawl tables through [CrawlRunner], audits every
 * screen the tables pass through, and finishes with the coverage check: every tag the crawl
 * saw on screen must be named by some crawl table, or be listed in [CrawlRegistry] with the
 * reason it is not crawled. Intents that leave the app (browser links) are stubbed, so a
 * crawl never opens another app and never reaches the network.
 */
abstract class CrawlTest(profile: FixtureProfile, completeOnboarding: Boolean = true) : AuditTest(profile, completeOnboarding) {
    protected val ctx: CrawlContext by lazy { CrawlContext(app) }
    private val runner by lazy { CrawlRunner(composeRule, ui, audit, player = { player }, screenshot = { screenshot(it) }) }

    @Before
    fun stubExternalIntents() {
        Intents.init()
        intending(not(isInternal())).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun releaseIntents() {
        Intents.release()
    }

    protected fun crawl(name: String, table: List<Step>) = runner.run(name, table)

    /** Fail on any untagged control seen, then on any tag seen that no table names. */
    protected fun finishCrawl() {
        audit.assertNoGaps()
        val unaccounted = audit.seenTags - CrawlRegistry.accountedFor(ctx)
        if (unaccounted.isNotEmpty()) {
            throw AssertionError(
                "tags on screen that no crawl table exercises: $unaccounted. Add a row to the screen's table " +
                    "(app/src/androidTest/.../surfaces/), or list the tag in CrawlRegistry.NOT_CRAWLED with the reason."
            )
        }
    }
}
