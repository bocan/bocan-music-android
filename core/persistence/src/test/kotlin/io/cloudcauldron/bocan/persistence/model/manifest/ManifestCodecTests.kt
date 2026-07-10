package io.cloudcauldron.bocan.persistence.model.manifest

import io.cloudcauldron.bocan.persistence.fixtureManifest
import io.cloudcauldron.bocan.persistence.readFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestCodecTests {
    @Test
    fun `fixture parses despite unknown keys`() {
        val manifest = fixtureManifest()
        assertEquals(1, manifest.protocolVersion)
        assertEquals(42, manifest.generation)
        assertEquals("Test Mac", manifest.serverName)
        assertEquals(4, manifest.tracks.size)
        assertEquals(3, manifest.playlists.size)
        assertEquals(1, manifest.podcasts.size)
        assertEquals(2, manifest.episodes.size)
    }

    @Test
    fun `clip track carries its source and bounds`() {
        val clip = fixtureManifest().tracks.single { it.id == 104L }.clip
        assertEquals(103L, clip?.sourceTrackId)
        assertEquals(0L, clip?.startMs)
        assertEquals(60_000L, clip?.endMs)
    }

    @Test
    fun `optional fields absent from json decode as nulls or defaults`() {
        val sparse = fixtureManifest().tracks.single { it.id == 102L }
        assertNull(sparse.year)
        assertNull(sparse.genre)
        assertNull(sparse.composer)
        assertNull(sparse.bpm)
        assertNull(sparse.replayGain)
        assertNull(sparse.lyricsHash)
        assertNull(sparse.clip)
        assertEquals(0, sparse.rating)

        val lossy = fixtureManifest().tracks.single { it.id == 103L }
        assertNull(lossy.bitDepth)
        assertNull(lossy.replayGain?.albumGain)
    }

    @Test
    fun `playlist kinds and hierarchy survive decoding`() {
        val manifest = fixtureManifest()
        assertEquals("folder", manifest.playlists.single { it.id == 1L }.kind)
        assertTrue(manifest.playlists.single { it.id == 1L }.trackIds.isEmpty())
        assertEquals("manual", manifest.playlists.single { it.id == 2L }.kind)
        assertEquals(1L, manifest.playlists.single { it.id == 2L }.parentId)
        assertEquals("smart", manifest.playlists.single { it.id == 3L }.kind)
        assertEquals(listOf(101L, 102L, 104L), manifest.playlists.single { it.id == 3L }.trackIds)
    }

    @Test
    fun `episode seed fields decode`() {
        val seeded = fixtureManifest().episodes.single { it.playState == "inProgress" }
        assertEquals(1_200_000L, seeded.playPositionMs)
        assertTrue(seeded.hasChapters)
    }

    @Test
    fun `manifest round-trips through encode and decode`() {
        val decoded = ManifestCodec.decode(readFixture("manifest-small.json"))
        val reDecoded = ManifestCodec.decode(ManifestCodec.encode(decoded))
        assertEquals(decoded, reDecoded)
    }
}
