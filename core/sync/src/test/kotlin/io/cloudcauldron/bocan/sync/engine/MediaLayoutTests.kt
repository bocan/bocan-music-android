package io.cloudcauldron.bocan.sync.engine

import androidx.test.core.app.ApplicationProvider
import io.cloudcauldron.bocan.sync.SyncError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaLayoutTests {
    private val layout = MediaLayout(ApplicationProvider.getApplicationContext())

    @Test
    fun `track file lands under media library`() {
        val file = layout.trackFile("Slowdive/Souvlaki/01 Alison.flac")
        val path = file.path.replace('\\', '/')
        assertTrue(path, path.endsWith("/media/library/Slowdive/Souvlaki/01 Alison.flac"))
    }

    @Test
    fun `episode file lands under media root with its podcast prefix`() {
        val file = layout.episodeFile("Podcasts/4/abcd.mp3")
        val path = file.path.replace('\\', '/')
        assertTrue(path, path.endsWith("/media/Podcasts/4/abcd.mp3"))
        assertTrue(path, !path.contains("/library/"))
    }

    @Test
    fun `relPath routing sends podcasts to the episode tree and everything else to library`() {
        val episode = layout.fileForRelPath("Podcasts/4/abcd.mp3").path.replace('\\', '/')
        val track = layout.fileForRelPath("Artist/Album/track.flac").path.replace('\\', '/')
        assertTrue(episode, episode.endsWith("/media/Podcasts/4/abcd.mp3"))
        assertTrue(track, track.endsWith("/media/library/Artist/Album/track.flac"))
    }

    @Test
    fun `parent traversal is rejected`() {
        assertThrows(SyncError.UnsafePath::class.java) { layout.trackFile("../../evil") }
    }

    @Test
    fun `leading slash is rejected`() {
        assertThrows(SyncError.UnsafePath::class.java) { layout.trackFile("/etc/passwd") }
    }

    @Test
    fun `empty segments and dot segments are rejected`() {
        assertThrows(SyncError.UnsafePath::class.java) { layout.trackFile("a//b.flac") }
        assertThrows(SyncError.UnsafePath::class.java) { layout.trackFile("a/./b.flac") }
        assertThrows(SyncError.UnsafePath::class.java) { layout.trackFile("") }
    }

    @Test
    fun `backslash is rejected so windows separators cannot smuggle traversal`() {
        assertThrows(SyncError.UnsafePath::class.java) { layout.trackFile("a\\..\\b") }
    }

    @Test
    fun `ordinary spaced filenames are allowed`() {
        assertEquals("01 Title.flac", layout.resolveRelPath("Artist/Album/01 Title.flac").substringAfterLast('/'))
    }

    @Test
    fun `media root and artwork dir resolve under external files`() {
        val root = layout.mediaRoot()
        requireNotNull(root)
        assertTrue(layout.artworkDir().path.replace('\\', '/').endsWith("/media/artwork"))
        assertTrue(layout.usableSpaceBytes()!! >= 0)
    }
}
