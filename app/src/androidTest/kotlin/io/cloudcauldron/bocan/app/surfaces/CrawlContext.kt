package io.cloudcauldron.bocan.app.surfaces

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import io.cloudcauldron.bocan.app.AppGraph
import io.cloudcauldron.bocan.app.BocanApplication
import io.cloudcauldron.bocan.app.E2ETest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * What a crawl table may refer to while it is built: the app's own strings (labels are never
 * typed into a table), the fixture's ids, and the app graph for postconditions that read
 * stored state. Building a table touches none of the graph; only its postconditions do.
 */
class CrawlContext(private val app: BocanApplication) {
    val graph: AppGraph get() = app.appGraph

    fun string(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String = app.resources.getQuantityString(id, count, *args)

    fun track(title: String): String = E2ETest.trackMediaId(title)

    fun episode(title: String): String = E2ETest.episodeMediaId(title)

    /** Every fixture track by [albumArtist], as media ids. */
    fun tracksBy(albumArtist: String): Set<String> =
        E2ETest.fixture.tracks.filter { it.albumArtist == albumArtist }.map { "track:${it.id}" }.toSet()

    /** The current value of a stored setting, for a [Postcondition.Holds]. */
    fun <T> now(flow: Flow<T>): T = runBlocking { flow.first() }
}
