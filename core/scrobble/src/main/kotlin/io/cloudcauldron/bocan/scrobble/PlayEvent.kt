package io.cloudcauldron.bocan.scrobble

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * One completed play, everything a provider needs to submit it. Serialised (minus the
 * transient [queueId]) into `scrobble_queue.payloadJson`; the queue reattaches the row
 * id as [queueId] when it reads a row back, so results can be matched to their rows.
 *
 * [playedAtEpochSec] is the play's start time captured at event time (never enqueue
 * time), the timestamp Last.fm and the ListenBrainz-compatible services record. There is
 * no MusicBrainz id: the synced track rows do not carry one, and every provider treats it
 * as optional.
 */
@Serializable
data class PlayEvent(
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumArtist: String? = null,
    val durationSec: Int,
    val playedAtEpochSec: Long,
    @Transient val queueId: Long = 0
)
