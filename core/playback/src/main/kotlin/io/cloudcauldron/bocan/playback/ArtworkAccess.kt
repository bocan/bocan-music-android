package io.cloudcauldron.bocan.playback

/**
 * Grants a connecting controller's process read access to the artwork content
 * Uris this app hands out in media metadata. Remote browsers (Android Auto above
 * all) load those Uris from their own process, and nothing grants that access
 * implicitly: Media3 never calls grantUriPermission, and the provider serving
 * the artwork is not exported. The session grants it explicitly on connect.
 * Implemented in :app, which owns the FileProvider; tests pass a fake.
 */
fun interface ArtworkAccess {
    /** Make every artwork Uri readable by [packageName]. Safe to call repeatedly. */
    fun grantReadTo(packageName: String)
}
