package io.cloudcauldron.bocan.app.demo

import io.cloudcauldron.bocan.persistence.model.manifest.Manifest
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestCodec
import io.cloudcauldron.bocan.playback.podcast.ChaptersParser
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The checked-in demo manifest must describe the checked-in files byte for
 * byte. This is the guard against regenerating one without the others; it runs
 * on the plain JVM so it is in every `test` invocation.
 */
class DemoAssetsIntegrityTests {
    private val manifest: Manifest = ManifestCodec.decode(File(DEMO_ASSET_DIR, "manifest.json").readText())

    @Test
    fun `manifest envelope is the demo server with no podcasts`() {
        assertEquals(1, manifest.protocolVersion)
        assertEquals("demo", manifest.serverId)
        assertEquals(1, manifest.podcasts.size)
        assertEquals(1, manifest.episodes.size)
        assertEquals(2, manifest.tracks.size)
        assertEquals(2, manifest.playlists.size)
    }

    @Test
    fun `the demo show and episode are complete and their chapters parse`() {
        val show = manifest.podcasts.single()
        val episode = manifest.episodes.single()
        assertTrue(show.id >= DemoLibrary.ID_BASE, "show id ${show.id}")
        assertEquals(show.id, episode.podcastId)
        assertTrue(episode.id.startsWith(DemoLibrary.EPISODE_ID_PREFIX), episode.id)
        assertTrue(episode.relPath.startsWith(DemoLibrary.EPISODE_REL_PATH_PREFIX), episode.relPath)
        listOf(show.author, show.descriptionHtml, show.artworkHash, episode.publishedAt, episode.durationMs, episode.descriptionHtml)
            .forEachIndexed { index, value -> assertTrue(value != null, "podcast field $index is null") }

        val file = File(DEMO_ASSET_DIR, episode.relPath)
        assertTrue(file.isFile, "missing ${episode.relPath}")
        assertEquals(episode.size, file.length())
        assertEquals(episode.sha256, sha256(file))
        val cover = File(DEMO_ASSET_DIR, "artwork/${show.artworkHash}")
        assertEquals(show.artworkHash, sha256(cover))

        assertTrue(episode.hasChapters)
        val chapters = ChaptersParser.parse(File(DEMO_ASSET_DIR, "chapters/${episode.id}.json").readText())
        assertTrue(chapters.size >= MIN_CHAPTERS, "only ${chapters.size} chapters")
        assertEquals(0L, chapters.first().startTimeMs)
        val duration = checkNotNull(episode.durationMs)
        assertTrue(chapters.all { it.startTimeMs < duration && it.title.isNotBlank() })
    }

    @Test
    fun `every track file exists with the declared size and sha256`() {
        manifest.tracks.forEach { track ->
            val file = File(DEMO_ASSET_DIR, "library/${track.relPath}")
            assertTrue(file.isFile, "missing ${track.relPath}")
            assertEquals(track.size, file.length(), "size of ${track.relPath}")
            assertEquals(track.sha256, sha256(file), "sha256 of ${track.relPath}")
            assertEquals("mp3", track.format)
        }
    }

    @Test
    fun `every track keeps to the demo id range and folder`() {
        manifest.tracks.forEach { track ->
            assertTrue(track.id >= DemoLibrary.ID_BASE, "id ${track.id}")
            assertTrue(track.relPath.startsWith(DemoLibrary.REL_PATH_PREFIX), track.relPath)
            assertTrue((track.artistId ?: 0) >= DemoLibrary.ID_BASE, "artistId of ${track.relPath}")
            assertTrue((track.albumId ?: 0) >= DemoLibrary.ID_BASE, "albumId of ${track.relPath}")
            assertEquals(null, track.clip)
        }
        manifest.playlists.forEach { playlist ->
            assertTrue(playlist.id >= DemoLibrary.ID_BASE, "playlist ${playlist.id}")
            assertTrue(playlist.trackIds.all { id -> manifest.tracks.any { it.id == id } }, "playlist ${playlist.name} members")
        }
    }

    @Test
    fun `every artwork hash names a file that hashes to it`() {
        val hashes = (manifest.tracks.mapNotNull { it.artworkHash } + manifest.playlists.mapNotNull { it.artworkHash }).toSet()
        assertEquals(2, hashes.size)
        hashes.forEach { hash ->
            val file = File(DEMO_ASSET_DIR, "artwork/$hash")
            assertTrue(file.isFile, "missing artwork $hash")
            assertEquals(hash, sha256(file))
        }
        assertEquals(2, manifest.tracks.map { it.artworkHash }.toSet().size, "each track has its own cover")
    }

    @Test
    fun `every lyrics hash matches its lrc file and the two differ`() {
        val texts = manifest.tracks.map { track ->
            val file = File(DEMO_ASSET_DIR, "lyrics/${track.id}.lrc")
            assertTrue(file.isFile, "missing lyrics for ${track.id}")
            assertEquals(track.lyricsHash, sha256(file), "lyricsHash of ${track.relPath}")
            file.readText()
        }
        assertEquals(2, texts.toSet().size)
        texts.forEach { text ->
            val timed = text.lines().count { it.matches(Regex("""\[\d\d:\d\d\.\d\d].+""")) }
            assertTrue(timed >= MIN_TIMED_LINES, "only $timed timed lines")
            assertTrue('\u2014' !in text && '\u2013' !in text, "dash in lyrics")
        }
    }

    @Test
    fun `every optional metadata field is filled`() {
        manifest.tracks.forEach { track ->
            listOf(
                track.title, track.artist, track.albumArtist, track.album, track.trackNumber, track.trackTotal,
                track.discNumber, track.discTotal, track.year, track.genre, track.composer, track.bpm,
                track.sampleRate, track.bitrate, track.channelCount, track.replayGain, track.artworkHash, track.lyricsHash
            ).forEachIndexed { index, value -> assertTrue(value != null, "field $index of ${track.relPath} is null") }
            assertTrue(track.durationMs in MIN_DURATION_MS..MAX_DURATION_MS, "duration ${track.durationMs}")
        }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MIN_TIMED_LINES = 12
        const val MIN_CHAPTERS = 3
        const val MIN_DURATION_MS = 58_000L
        const val MAX_DURATION_MS = 62_000L
    }
}
