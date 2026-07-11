package io.cloudcauldron.bocan.playback.podcast

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches a Podcasting 2.0 chapters document for an episode from the paired Mac. The :app
 * module implements this over :core:sync's paired client, since :core:playback must not
 * import a sibling module. Returns the raw JSON body, or null when absent or unreachable.
 */
fun interface ChaptersFetcher {
    suspend fun fetch(episodeId: String): String?
}

/**
 * Resolves chapters for an episode: fetches from the Mac only when the manifest says the
 * episode has chapters, parses with [ChaptersParser], and caches the result in memory for
 * the session (chapters do not change within a run). Never blocks playback.
 */
class ChaptersRepository(private val fetcher: ChaptersFetcher, private val dispatchers: CoroutineDispatchers, private val log: AppLog) {
    private val cache = mutableMapOf<String, List<Chapter>>()

    suspend fun chaptersFor(episodeId: String, hasChapters: Boolean): List<Chapter> = withContext(dispatchers.io) {
        if (!hasChapters) return@withContext emptyList()
        cache[episodeId]?.let { return@withContext it }
        val body = fetcher.fetch(episodeId)
        if (body == null) {
            log.debug("chapters.unreachable", mapOf("episodeId" to episodeId))
            return@withContext emptyList()
        }
        val chapters = ChaptersParser.parse(body)
        cache[episodeId] = chapters
        chapters
    }
}
