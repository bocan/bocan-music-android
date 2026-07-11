package io.cloudcauldron.bocan.playback.stats

import java.time.Instant

/**
 * A completed play worth scrobbling, emitted on a SharedFlow for phase 09. Podcast
 * episodes are flagged so the scrobbler skips them: podcasts are not scrobbled.
 * Clip tracks carry their own clip track id, never the source track id.
 */
data class ScrobbleEvent(val mediaId: String, val trackId: Long?, val playedAt: Instant, val isPodcast: Boolean)
