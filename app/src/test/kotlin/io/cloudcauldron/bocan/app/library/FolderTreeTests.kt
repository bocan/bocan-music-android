package io.cloudcauldron.bocan.app.library

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class FolderTreeTests {
    private fun item(id: Long, path: String) = FolderTree.Item(id, path)

    @Test
    fun `root lists top level folders only, sorted`() {
        val items = listOf(
            item(1, "Zappa/Album/01.flac"),
            item(2, "ABBA/Album/01.flac"),
            item(3, "ABBA/Other/02.flac")
        )
        val entries = FolderTree.childrenOf(items, emptyList())
        assertEquals(
            listOf("ABBA", "Zappa"),
            entries.filterIsInstance<FolderTree.Entry.Folder>().map { it.name }
        )
    }

    @Test
    fun `descending shows subfolders then tracks`() {
        val items = listOf(
            item(1, "Artist/Album/03 Song.flac"),
            item(2, "Artist/Album/01 Intro.flac"),
            item(3, "Artist/Live/gig.flac")
        )
        val atArtist = FolderTree.childrenOf(items, listOf("Artist"))
        assertEquals(listOf("Album", "Live"), atArtist.filterIsInstance<FolderTree.Entry.Folder>().map { it.name })

        val atAlbum = FolderTree.childrenOf(items, listOf("Artist", "Album"))
        val tracks = atAlbum.filterIsInstance<FolderTree.Entry.Track>()
        assertEquals(listOf("01 Intro.flac", "03 Song.flac"), tracks.map { it.name })
        assertEquals(listOf(2L, 1L), tracks.map { it.trackId })
    }

    @Test
    fun `a single-file root contributes a track at the root`() {
        val entries = FolderTree.childrenOf(listOf(item(9, "loose.flac")), emptyList())
        assertEquals(1, entries.size)
        assertTrue(entries.single() is FolderTree.Entry.Track)
    }

    @Test
    fun `unicode segments are preserved`() {
        val items = listOf(item(1, "Bjork/Vespertine/track.flac"), item(2, "Sigur Ros/track.flac"))
        val roots = FolderTree.childrenOf(items, emptyList()).filterIsInstance<FolderTree.Entry.Folder>().map { it.name }
        assertTrue("Bjork" in roots)
        assertTrue("Sigur Ros" in roots)
    }

    @Test
    fun `duplicate subfolders collapse to one`() {
        val items = listOf(
            item(1, "A/B/1.flac"),
            item(2, "A/B/2.flac"),
            item(3, "A/B/3.flac")
        )
        val atA = FolderTree.childrenOf(items, listOf("A"))
        assertEquals(1, atA.filterIsInstance<FolderTree.Entry.Folder>().size)
        assertEquals(listOf("B"), atA.filterIsInstance<FolderTree.Entry.Folder>().map { it.name })
    }

    @Test
    fun `folder segments carry the full path for navigation`() {
        val items = listOf(item(1, "A/B/C/x.flac"))
        val folder = FolderTree.childrenOf(items, listOf("A")).filterIsInstance<FolderTree.Entry.Folder>().single()
        assertEquals(listOf("A", "B"), folder.segments)
    }
}
