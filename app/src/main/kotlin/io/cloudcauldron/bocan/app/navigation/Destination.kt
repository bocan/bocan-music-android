package io.cloudcauldron.bocan.app.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes (Navigation Compose + kotlinx.serialization). The four
 * top-level routes back the bottom bar; the rest push onto the stack. Detail routes
 * carry their id and expose a `bocan://` deep link the widget uses in a later phase.
 */
sealed interface Destination {
    @Serializable
    data object Library : Destination

    @Serializable
    data object Podcasts : Destination

    @Serializable
    data object Search : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data class AlbumDetail(val albumId: Long) : Destination

    @Serializable
    data class ArtistDetail(val artistId: Long) : Destination

    @Serializable
    data class PlaylistDetail(val playlistId: Long) : Destination

    @Serializable
    data class GenreDetail(val genre: String) : Destination

    /** Placeholder route filled by phase 06; the mini player taps here. */
    @Serializable
    data object NowPlaying : Destination

    @Serializable
    data object Pairing : Destination

    @Serializable
    data object SyncStatus : Destination

    companion object {
        const val DEEP_LINK_ALBUM = "bocan://album"
        const val DEEP_LINK_ARTIST = "bocan://artist"
        const val DEEP_LINK_PLAYLIST = "bocan://playlist"
    }
}

/** The four bottom-bar destinations, in display order. */
val bottomDestinations: List<Destination> = listOf(
    Destination.Library,
    Destination.Podcasts,
    Destination.Search,
    Destination.Settings
)
