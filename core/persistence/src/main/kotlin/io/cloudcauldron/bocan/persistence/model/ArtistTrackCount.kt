package io.cloudcauldron.bocan.persistence.model

/**
 * A per-artist track count, computed by the database with COUNT ... GROUP BY rather than by
 * loading every track into memory, so the artists list scales to a large library.
 */
data class ArtistTrackCount(val artistId: Long, val count: Int)
