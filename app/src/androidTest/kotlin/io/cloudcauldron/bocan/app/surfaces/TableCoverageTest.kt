package io.cloudcauldron.bocan.app.surfaces

import androidx.test.core.app.ApplicationProvider
import io.cloudcauldron.bocan.app.BocanApplication
import io.cloudcauldron.bocan.app.e2e.E2ETags
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The static half of the coverage check (phase 17): every tag constant in [E2ETags] is
 * exercised by a crawl table step, or listed in [CrawlRegistry] with its reason; and nothing
 * listed there is crawled after all. The runtime half is `CrawlTest.finishCrawl`, which fails
 * on a tag seen on screen that no table names (including generated tags such as a scrobble
 * provider's).
 */
class TableCoverageTest {
    private val ctx = CrawlContext(ApplicationProvider.getApplicationContext<BocanApplication>())

    @Test
    fun everyTagIsCrawledOrExplained() {
        val missing = allTagConstants() - CrawlRegistry.accountedFor(ctx)
        assertEquals(emptySet(), missing, "E2ETags constants that no crawl table steps on and CrawlRegistry does not explain")
    }

    @Test
    fun noExplainedTagIsCrawledAfterAll() {
        val stale = (CrawlRegistry.NOT_CRAWLED.keys + CrawlRegistry.NOT_ACTIONABLE) intersect CrawlRegistry.stepTags(ctx)
        assertEquals(emptySet(), stale, "tags CrawlRegistry lists as not crawled, which a table does step on")
    }

    @Test
    fun everyStepNamesARealTag() {
        val known = allTagConstants() + CrawlTags.ALL
        val unknown = CrawlRegistry.stepTags(ctx).filterNot { it in known || it.startsWith("scrobble.provider.") }
        assertTrue(unknown.isEmpty(), "steps on tags that are not in E2ETags: $unknown")
    }

    /** Every `const val` tag in the nested objects of [E2ETags]; dot-less values are parts of a tag, not tags. */
    private fun allTagConstants(): Set<String> = E2ETags::class.java.declaredClasses
        .flatMap { holder -> holder.declaredFields.filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java } }
        .map { it.get(null) as String }
        .filter { '.' in it }
        .toSet()
}
