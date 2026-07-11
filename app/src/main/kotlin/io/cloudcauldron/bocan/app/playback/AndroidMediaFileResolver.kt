package io.cloudcauldron.bocan.app.playback

import android.net.Uri
import io.cloudcauldron.bocan.playback.MediaFileResolver
import io.cloudcauldron.bocan.sync.engine.ArtworkStore
import io.cloudcauldron.bocan.sync.engine.MediaLayout

/**
 * Bridges :core:playback's [MediaFileResolver] seam to :core:sync's [MediaLayout]
 * and [ArtworkStore]. This lives in :app because playback and sync are sibling
 * modules that must not import one another; :app depends on both and wires them.
 * The path validation and media-root logic stay in [MediaLayout].
 */
class AndroidMediaFileResolver(private val mediaLayout: MediaLayout, private val artworkStore: ArtworkStore) : MediaFileResolver {
    override fun trackUri(relPath: String): Uri = Uri.fromFile(mediaLayout.trackFile(relPath))

    override fun episodeUri(relPath: String): Uri = Uri.fromFile(mediaLayout.episodeFile(relPath))

    override fun artworkUri(hash: String): Uri? = artworkStore.existing(hash)?.let(Uri::fromFile)
}
