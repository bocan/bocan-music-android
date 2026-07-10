package io.cloudcauldron.bocan.persistence.model

import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity

/** Grouped results of one FTS search over the library. */
data class SearchResults(val tracks: List<TrackEntity>, val albums: List<AlbumEntity>, val artists: List<ArtistEntity>) {
    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), emptyList())
    }
}
