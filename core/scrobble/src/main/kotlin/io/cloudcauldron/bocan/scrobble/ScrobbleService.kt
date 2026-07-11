package io.cloudcauldron.bocan.scrobble

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.scrobble.providers.ScrobbleProvider
import io.cloudcauldron.bocan.scrobble.queue.ScrobbleQueue
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The track metadata a scrobble needs, resolved from the synced library by track id. */
data class ScrobbleTrack(val title: String, val artist: String, val album: String?, val albumArtist: String?, val durationSec: Int)

/**
 * The orchestrator. It receives eligible plays and track-start signals from the app (which
 * bridges the phase 04 stats event flow, since :core:scrobble cannot import :core:playback),
 * resolves metadata, and drives the queue and providers. It never blocks or runs on the
 * audio thread: every entry point hops onto a worker coroutine immediately (phase 09 gotcha).
 *
 * Podcasts never scrobble: episode events carry [isPodcast] and are dropped here as well as
 * upstream, so the rule holds even if a caller forgets it. Now-playing is best-effort and
 * unqueued; a completed eligible play is enqueued for every enabled provider, then drained.
 */
class ScrobbleService(
    private val providers: List<ScrobbleProvider>,
    private val queue: ScrobbleQueue,
    private val metadata: suspend (Long) -> ScrobbleTrack?,
    private val enabledProviders: suspend () -> Set<String>,
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers
) {
    private val byId = providers.associateBy { it.id }
    private val log = AppLog.forCategory(LogCategory.Scrobble)

    /** A completed, eligible play: enqueue it for every enabled provider, then drain. */
    fun onPlayEligible(trackId: Long, playedAt: Instant, isPodcast: Boolean) {
        if (isPodcast) return
        scope.launch(dispatchers.io) {
            val enabled = enabledProviders()
            if (enabled.isEmpty()) return@launch
            val play = buildPlay(trackId, playedAt) ?: return@launch
            enabled.forEach { providerId -> queue.enqueue(providerId, play) }
            queue.drain(byId, enabled)
        }
    }

    /** A track just started: send a best-effort now-playing to every enabled, authed provider. */
    fun onNowPlaying(trackId: Long, isPodcast: Boolean) {
        if (isPodcast) return
        scope.launch(dispatchers.io) {
            val enabled = enabledProviders()
            if (enabled.isEmpty()) return@launch
            val play = buildPlay(trackId, Instant.EPOCH) ?: return@launch
            enabled.forEach { providerId ->
                val provider = byId[providerId] ?: return@forEach
                runCatching { provider.updateNowPlaying(play) }
                    .onFailure { log.debug("scrobble.nowplaying.failed", mapOf("provider" to providerId, "error" to it.toString())) }
            }
        }
    }

    /** Drain the queue (called on connectivity regained and app foreground). */
    fun drain() {
        scope.launch(dispatchers.io) { queue.drain(byId, enabledProviders()) }
    }

    private suspend fun buildPlay(trackId: Long, playedAt: Instant): PlayEvent? {
        val track = metadata(trackId) ?: return null
        return PlayEvent(
            trackId = trackId,
            title = track.title,
            artist = track.artist,
            album = track.album,
            albumArtist = track.albumArtist,
            durationSec = track.durationSec,
            playedAtEpochSec = playedAt.epochSecond
        )
    }
}
