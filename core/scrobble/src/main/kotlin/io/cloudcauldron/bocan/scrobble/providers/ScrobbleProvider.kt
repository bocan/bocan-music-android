package io.cloudcauldron.bocan.scrobble.providers

import io.cloudcauldron.bocan.scrobble.AuthState
import io.cloudcauldron.bocan.scrobble.PlayEvent
import io.cloudcauldron.bocan.scrobble.SubmissionResult
import kotlinx.coroutines.flow.Flow

/** Stable provider ids, used as the `provider` column in `scrobble_queue`. */
object ProviderId {
    const val LAST_FM = "lastfm"
    const val LISTENBRAINZ = "listenbrainz"
    const val ROCKSKY = "rocksky"
}

/**
 * The sending end of the pipeline: one implementation per service. Every method is
 * main-safe and does its network work on an IO dispatcher; the queue worker calls
 * [scrobble] and the service calls [updateNowPlaying].
 *
 * [updateNowPlaying] is best-effort and never queued: a failure is logged, not retried.
 * [scrobble] returns one [SubmissionResult] per input play, in order, so the queue can
 * settle each row independently. [authState] drives the settings connect/disconnect UI.
 */
interface ScrobbleProvider {
    /** Stable id (`lastfm`, `listenbrainz`, `rocksky`). */
    val id: String

    /** Human-readable name for settings. */
    val displayName: String

    /** The current connection state, observed by settings. */
    val authState: Flow<AuthState>

    /** Whether the provider currently has usable credentials (no network round-trip). */
    suspend fun isAuthenticated(): Boolean

    /** Send a best-effort now-playing notification for a track that just started. */
    suspend fun updateNowPlaying(play: PlayEvent)

    /** Submit a batch of completed plays; returns one result per play, in order. */
    suspend fun scrobble(batch: List<PlayEvent>): List<SubmissionResult>
}
