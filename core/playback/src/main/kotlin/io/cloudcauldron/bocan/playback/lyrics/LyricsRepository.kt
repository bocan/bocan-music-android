package io.cloudcauldron.bocan.playback.lyrics

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.persistence.daos.LyricsDao
import io.cloudcauldron.bocan.persistence.entities.LyricsCacheEntity
import io.cloudcauldron.bocan.persistence.model.LyricsKind
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import java.time.Instant
import kotlinx.coroutines.withContext

/** The lyrics available for the current track. */
sealed interface LyricsResult {
    /** No lyrics: none on the Mac, or offline with nothing cached. */
    data object None : LyricsResult

    /** A parsed lyrics document to render. */
    data class Loaded(val doc: LyricsDoc) : LyricsResult
}

/**
 * Resolves lyrics for a track: cache first by lyricsHash, then the paired Mac on a miss
 * or hash change, caching the result. A 404 is remembered for the session so it is not
 * refetched; offline with no cache yields [LyricsResult.None]; offline with a stale
 * cache serves the stale copy rather than nothing. Never blocks playback.
 */
class LyricsRepository(
    private val lyricsDao: LyricsDao,
    private val fetcher: LyricsFetcher,
    private val dispatchers: CoroutineDispatchers,
    private val log: AppLog,
    private val now: () -> Instant = Instant::now
) {
    private val absentThisSession = mutableSetOf<Long>()

    /**
     * Lyrics for [trackId] whose manifest lyrics hash is [lyricsHash] (null when the track
     * has no lyrics at all).
     */
    suspend fun lyricsFor(trackId: Long, lyricsHash: String?): LyricsResult = withContext(dispatchers.io) {
        if (lyricsHash == null || trackId in absentThisSession) return@withContext LyricsResult.None
        val cached = lyricsDao.get(trackId)
        if (cached != null && cached.lyricsHash == lyricsHash) {
            return@withContext parse(cached.kind, cached.text)
        }
        when (val fetched = fetcher.fetch(trackId)) {
            is FetchResult.Found -> {
                lyricsDao.upsert(LyricsCacheEntity(trackId, lyricsHash, fetched.kind, fetched.text, now()))
                parse(fetched.kind, fetched.text)
            }
            FetchResult.NotFound -> {
                absentThisSession += trackId
                LyricsResult.None
            }
            FetchResult.Unreachable -> {
                log.debug("lyrics.unreachable", mapOf("trackId" to trackId))
                if (cached != null) parse(cached.kind, cached.text) else LyricsResult.None
            }
        }
    }

    private fun parse(kind: LyricsKind, text: String): LyricsResult = when (kind) {
        LyricsKind.Synced -> LyricsResult.Loaded(LrcParser.parse(text))
        LyricsKind.Unsynced -> LyricsResult.Loaded(LyricsDoc.Unsynced(text))
    }
}
