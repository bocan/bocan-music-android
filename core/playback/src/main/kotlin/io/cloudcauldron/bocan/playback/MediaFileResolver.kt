package io.cloudcauldron.bocan.playback

import android.net.Uri

/**
 * Resolves a synced item's relative path (and content-addressed artwork hash) to
 * a concrete on-disk [Uri]. The path convention and defensive validation live in
 * :core:sync (MediaLayout, ArtworkStore); :core:playback must not import a sibling
 * module, so :app implements this seam over those classes. Tests pass a fake.
 */
interface MediaFileResolver {
    /** A playable file Uri for a track's relPath (the source file for a clip track). */
    fun trackUri(relPath: String): Uri

    /** A playable file Uri for an episode's relPath. */
    fun episodeUri(relPath: String): Uri

    /** The on-disk artwork Uri for a content hash, or null if it is not present. */
    fun artworkUri(hash: String): Uri?
}
