package io.cloudcauldron.bocan.app.library

/**
 * The folder browser is derived from track relPaths in memory, never from SQL: the
 * database does not model directories. Given a set of tracks and the folder the user
 * is currently viewing, [childrenOf] returns the immediate subfolders and the tracks
 * that live directly in that folder.
 *
 * A relPath like "Artist/Album/03 Song.flac" contributes the path segments
 * ["Artist", "Album"] as nested folders and the track as a leaf in "Artist/Album".
 * Paths are split on '/' (the manifest's separator); the file name segment is never a
 * folder.
 */
object FolderTree {
    /** One entry in a folder listing: either a subfolder or a track that lives here. */
    sealed interface Entry {
        /** A subfolder [name], with its full path [segments] from the root for navigation. */
        data class Folder(val name: String, val segments: List<String>) : Entry

        /** A track [trackId] that lives directly in the viewed folder, with its file [name]. */
        data class Track(val trackId: Long, val name: String) : Entry
    }

    /** A minimal view of a track for folder derivation, decoupled from the Room entity. */
    data class Item(val trackId: Long, val relPath: String)

    /**
     * The immediate children of [folder] (a list of path segments; empty for the root),
     * folders first (case-insensitive by name), then tracks (by file name). Duplicate
     * subfolders are collapsed.
     */
    fun childrenOf(items: List<Item>, folder: List<String>): List<Entry> {
        val folders = LinkedHashMap<String, Entry.Folder>()
        val tracks = ArrayList<Entry.Track>()
        for (item in items) {
            val segments = item.relPath.split('/').filter { it.isNotEmpty() }
            if (segments.size <= folder.size || !startsWith(segments, folder)) continue
            val next = segments[folder.size]
            val isLeaf = segments.size == folder.size + 1
            if (isLeaf) {
                tracks += Entry.Track(item.trackId, next)
            } else {
                folders.getOrPut(next) { Entry.Folder(next, folder + next) }
            }
        }
        val sortedFolders = folders.values.sortedBy { it.name.lowercase() }
        val sortedTracks = tracks.sortedBy { it.name.lowercase() }
        return sortedFolders + sortedTracks
    }

    private fun startsWith(segments: List<String>, prefix: List<String>): Boolean =
        segments.size >= prefix.size && prefix.indices.all { segments[it] == prefix[it] }
}
