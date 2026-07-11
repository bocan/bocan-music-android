package io.cloudcauldron.bocan.playback.lyrics

import io.cloudcauldron.bocan.persistence.model.LyricsKind

/** The outcome of asking the paired Mac for a track's lyrics. */
sealed interface FetchResult {
    /** The Mac returned a lyrics document. */
    data class Found(val kind: LyricsKind, val text: String) : FetchResult

    /** The Mac has no lyrics for this track (HTTP 404): cache negatively for the session. */
    data object NotFound : FetchResult

    /** The Mac could not be reached (offline, timeout): retry later, do not cache. */
    data object Unreachable : FetchResult
}

/**
 * Fetches lyrics for a track from the paired Mac. This is a boundary the :app module
 * implements over :core:sync's paired HTTP client, since :core:playback must not import
 * a sibling module. Never throws: transport failures come back as [FetchResult.Unreachable].
 */
fun interface LyricsFetcher {
    suspend fun fetch(trackId: Long): FetchResult
}
