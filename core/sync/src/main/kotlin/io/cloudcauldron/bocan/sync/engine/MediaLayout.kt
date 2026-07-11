package io.cloudcauldron.bocan.sync.engine

import android.content.Context
import io.cloudcauldron.bocan.sync.SyncError
import java.io.File

/**
 * Maps a manifest relPath to a concrete file under app-specific external storage,
 * and validates every path defensively before it touches the filesystem.
 *
 * The media root is `getExternalFilesDir(null)/media`. Tracks live at
 * `media/library/<relPath>`, episodes at `media/<relPath>` (episode relPaths
 * already start with `Podcasts/`), artwork at `media/artwork/<hash>`.
 *
 * The Mac already sanitizes relPaths, but a hostile or buggy manifest must never
 * escape the media root, so [resolveRelPath] rejects `..`, leading slashes,
 * backslashes, and empty segments and throws [SyncError.UnsafePath].
 * [SyncError.MediaUnavailable] surfaces when external storage is unmounted rather
 * than letting a null root NPE.
 */
class MediaLayout(private val context: Context) {
    /** The media root, or null if external storage is currently unavailable. */
    fun mediaRoot(): File? = context.getExternalFilesDir(null)?.let { File(it, MEDIA_DIR) }

    /** Usable bytes at the media root, or null if storage is unavailable. */
    fun usableSpaceBytes(): Long? = mediaRoot()?.let { root ->
        root.mkdirs()
        root.usableSpace
    }

    /** The directory content-addressed artwork lives in, created on demand. */
    fun artworkDir(): File = File(requireRoot(), ARTWORK_DIR).also { it.mkdirs() }

    /** `media/library/<relPath>` for a track, with the relPath validated. */
    fun trackFile(relPath: String): File = File(File(requireRoot(), LIBRARY_DIR), resolveRelPath(relPath))

    /** `media/<relPath>` for an episode (relPath already starts with `Podcasts/`). */
    fun episodeFile(relPath: String): File = File(requireRoot(), resolveRelPath(relPath))

    /**
     * Route a relPath to its file by convention: episode paths start with
     * `Podcasts/` and live under the media root, everything else is a track under
     * `library/`. Used for post-apply deletes where only the relPath is known.
     */
    fun fileForRelPath(relPath: String): File = if (relPath.startsWith(PODCASTS_PREFIX)) episodeFile(relPath) else trackFile(relPath)

    /**
     * Remove now-empty directories under `library/` and `Podcasts/` after a sync
     * deletes departed files. The roots themselves are kept.
     */
    fun pruneEmptyDirs() {
        val root = mediaRoot() ?: return
        listOf(File(root, LIBRARY_DIR), File(root, PODCASTS_PREFIX.trimEnd('/'))).forEach { pruneWithin(it) }
    }

    private fun pruneWithin(dir: File) {
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { child -> if (child.isDirectory) pruneWithin(child) }
        // Never delete the top-level dir itself; only empty descendants below it.
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory && child.list()?.isEmpty() == true) child.delete()
        }
    }

    private fun requireRoot(): File = mediaRoot() ?: throw SyncError.MediaUnavailable

    /** Validate a relPath and return it, or throw [SyncError.UnsafePath]. */
    fun resolveRelPath(relPath: String): String {
        val unsafe = relPath.isEmpty() ||
            relPath.startsWith("/") ||
            relPath.contains('\\') ||
            relPath.split('/').any { it.isEmpty() || it == "." || it == ".." }
        if (unsafe) throw SyncError.UnsafePath(relPath)
        return relPath
    }

    private companion object {
        const val MEDIA_DIR = "media"
        const val LIBRARY_DIR = "library"
        const val ARTWORK_DIR = "artwork"
        const val PODCASTS_PREFIX = "Podcasts/"
    }
}
