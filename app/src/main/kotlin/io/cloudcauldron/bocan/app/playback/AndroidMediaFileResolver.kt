package io.cloudcauldron.bocan.app.playback

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.cloudcauldron.bocan.playback.MediaFileResolver
import io.cloudcauldron.bocan.sync.engine.ArtworkStore
import io.cloudcauldron.bocan.sync.engine.MediaLayout

/**
 * Bridges :core:playback's [MediaFileResolver] seam to :core:sync's [MediaLayout]
 * and [ArtworkStore]. This lives in :app because playback and sync are sibling
 * modules that must not import one another; :app depends on both and wires them.
 * The path validation and media-root logic stay in [MediaLayout].
 */
class AndroidMediaFileResolver(private val context: Context, private val mediaLayout: MediaLayout, private val artworkStore: ArtworkStore) :
    MediaFileResolver {
    // Tracks and episodes are read by ExoPlayer inside this process, so a file:// Uri is fine.
    override fun trackUri(relPath: String): Uri = Uri.fromFile(mediaLayout.trackFile(relPath))

    override fun episodeUri(relPath: String): Uri = Uri.fromFile(mediaLayout.episodeFile(relPath))

    // Artwork is read by other processes (Android Auto, the system media UI) that cannot open
    // an app-private file:// path, so hand out a content:// Uri from the FileProvider. Media3
    // grants the connected controller read on it; the notification and in-app Coil load it too.
    override fun artworkUri(hash: String): Uri? = artworkStore.existing(hash)?.let { file ->
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
