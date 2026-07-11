package io.cloudcauldron.bocan.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.model.PlayState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SyncApplierTests {
    @Test
    fun `fresh database lands everything pending with derived albums and artists`() = runDbTest { db ->
        val plan = fixedClockApplier(db).apply(fixtureManifest())

        val tracks = db.syncDao().allTracks()
        assertEquals(5, tracks.size)
        assertTrue(tracks.all { it.downloadState == DownloadState.Pending })
        assertEquals(listOf(101L, 102L, 103L, 105L), plan.tracksToDownload.map { it.id })
        assertEquals(3, plan.episodesToDownload.size)
        assertTrue(plan.relPathsToDelete.isEmpty())
        assertEquals(3, plan.artworkHashesNeeded.size)

        val albums = db.libraryDao().observeAlbumsByName().firstList()
        val loveless = albums.single { it.id == 55L }
        assertEquals("Loveless", loveless.name)
        assertEquals("My Bloody Valentine", loveless.albumArtistName)
        assertEquals(1991, loveless.year)
        assertEquals(2, loveless.trackCount)
        assertEquals(
            "c0ffee01c0ffee01c0ffee01c0ffee01c0ffee01c0ffee01c0ffee01c0ffee01",
            loveless.artworkHash
        )
        val souvlaki = albums.single { it.id == 56L }
        assertEquals(1993, souvlaki.year)
        assertEquals(2, souvlaki.trackCount)
        assertNull(souvlaki.artworkHash)

        assertEquals(listOf(UNKNOWN_ID, 7L, 8L), db.libraryDao().observeArtists().firstList().map { it.id })
    }

    @Test
    fun `untagged track and undated episode normalize into fallbacks`() = runDbTest { db ->
        fixedClockApplier(db).apply(fixtureManifest())

        val untagged = db.syncDao().allTracks().single { it.id == 105L }
        assertEquals("rip-004", untagged.title)
        assertEquals(UNKNOWN_ID, untagged.artistId)
        assertEquals("", untagged.artistName)
        assertEquals(UNKNOWN_ID, untagged.albumId)
        assertEquals("", untagged.albumName)

        val unknownAlbum = db.libraryDao().observeAlbumsByName().firstList().single { it.id == UNKNOWN_ID }
        assertEquals("", unknownAlbum.name)
        assertEquals(1, unknownAlbum.trackCount)

        val undated = db.syncDao().allEpisodes().single { it.id == "a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8" }
        assertEquals(Instant.EPOCH, undated.publishedAt)
        assertEquals(0L, undated.durationMs)
    }

    @Test
    fun `re-applying the same manifest changes nothing and plans nothing`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        applier.apply(fixtureManifest())
        val before = db.syncDao().allTracks() to db.syncDao().allEpisodes()

        val plan = applier.apply(fixtureManifest())

        assertTrue(plan.tracksToDownload.isEmpty())
        assertTrue(plan.episodesToDownload.isEmpty())
        assertTrue(plan.artworkHashesNeeded.isEmpty())
        assertTrue(plan.relPathsToDelete.isEmpty())
        assertEquals(before, db.syncDao().allTracks() to db.syncDao().allEpisodes())
    }

    @Test
    fun `sha256 change replans the download and keeps play stats`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        applier.apply(fixtureManifest())
        applier.markDownloaded(listOf(101L, 102L, 103L), emptyList())
        db.playStatsDao().recordPlay(101L, playedSec = 200, at = FIXED_NOW)

        val changed = fixtureManifest().let { m ->
            m.copy(tracks = m.tracks.map { if (it.id == 101L) it.copy(sha256 = "ff01".repeat(16)) else it })
        }
        val plan = applier.apply(changed)

        assertEquals(listOf(101L), plan.tracksToDownload.map { it.id })
        val track = db.syncDao().allTracks().single { it.id == 101L }
        assertEquals(DownloadState.Pending, track.downloadState)
        assertEquals("ff01".repeat(16), track.sha256)
        assertEquals(1L, db.playStatsDao().stats(101L)?.playCount)
    }

    @Test
    fun `departing track is deleted with its file path while play stats survive`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        applier.apply(fixtureManifest())
        db.playStatsDao().recordPlay(101L, playedSec = 100, at = FIXED_NOW)

        val without101 = fixtureManifest().let { m ->
            m.copy(
                tracks = m.tracks.filterNot { it.id == 101L },
                playlists = m.playlists.map { p -> p.copy(trackIds = p.trackIds.filterNot { it == 101L }) }
            )
        }
        val plan = applier.apply(without101)

        assertNull(db.syncDao().allTracks().find { it.id == 101L })
        assertEquals(listOf("My Bloody Valentine/Loveless/01 Only Shallow.flac"), plan.relPathsToDelete)
        assertEquals(1L, db.playStatsDao().stats(101L)?.playCount)
    }

    @Test
    fun `rejoining track keeps its historical play stats`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        applier.apply(fixtureManifest())
        db.playStatsDao().recordPlay(101L, playedSec = 100, at = FIXED_NOW)
        applier.apply(
            fixtureManifest().let { m ->
                m.copy(
                    tracks = m.tracks.filterNot { it.id == 101L },
                    playlists = m.playlists.map { p -> p.copy(trackIds = p.trackIds.filterNot { it == 101L }) }
                )
            }
        )

        applier.apply(fixtureManifest())

        assertEquals(1L, db.playStatsDao().stats(101L)?.playCount)
    }

    @Test
    fun `metadata-only change updates the row without planning a download`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        applier.apply(fixtureManifest())
        applier.markDownloaded(listOf(101L, 102L, 103L), emptyList())

        val rerated = fixtureManifest().let { m ->
            m.copy(tracks = m.tracks.map { if (it.id == 103L) it.copy(rating = 80) else it })
        }
        val plan = applier.apply(rerated)

        assertTrue(plan.tracksToDownload.isEmpty())
        val track = db.syncDao().allTracks().single { it.id == 103L }
        assertEquals(80, track.rating)
        assertEquals(DownloadState.Downloaded, track.downloadState)
    }

    @Test
    fun `clips are never planned and inherit the source download state`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        val plan = applier.apply(fixtureManifest())
        assertTrue(plan.tracksToDownload.none { it.id == 104L })

        applier.markDownloaded(listOf(103L), emptyList())
        assertEquals(
            DownloadState.Downloaded,
            db.syncDao().allTracks().single { it.id == 104L }.downloadState
        )

        val reShaed = fixtureManifest().let { m ->
            m.copy(tracks = m.tracks.map { if (it.id == 103L) it.copy(sha256 = "ee03".repeat(16)) else it })
        }
        val rePlan = applier.apply(reShaed)
        assertEquals(listOf(103L), rePlan.tracksToDownload.map { it.id })
        assertEquals(
            DownloadState.Pending,
            db.syncDao().allTracks().single { it.id == 104L }.downloadState
        )
    }

    @Test
    fun `deleting the source track also removes its clips`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        applier.apply(fixtureManifest())

        // The manifest still lists clip 104, but its source 103 departed.
        val sourceGone = fixtureManifest().let { m ->
            m.copy(
                tracks = m.tracks.filterNot { it.id == 103L },
                playlists = m.playlists.map { p -> p.copy(trackIds = p.trackIds.filterNot { it == 103L || it == 104L }) }
            )
        }
        val plan = applier.apply(sourceGone)

        val remaining = db.syncDao().allTracks().map { it.id }
        assertTrue(103L !in remaining)
        assertTrue(104L !in remaining)
        assertEquals(listOf("Slowdive/Souvlaki/04 Souvlaki Space Station.mp3"), plan.relPathsToDelete)
    }

    @Test
    fun `episode state seeds once and never again`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        applier.apply(fixtureManifest())

        val seededId = "e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6"
        val seeded = db.episodeStateDao().state(seededId)
        assertEquals(1_200_000L, seeded?.playPositionMs)
        assertEquals(PlayState.InProgress, seeded?.playState)

        val macMovedOn = fixtureManifest().let { m ->
            m.copy(
                episodes = m.episodes.map {
                    if (it.id == seededId) it.copy(playPositionMs = 3_000_000, playState = "played") else it
                }
            )
        }
        applier.apply(macMovedOn)

        val after = db.episodeStateDao().state(seededId)
        assertEquals(1_200_000L, after?.playPositionMs)
        assertEquals(PlayState.InProgress, after?.playState)
    }

    @Test
    fun `playlist reorder is applied position for position`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        applier.apply(fixtureManifest())
        assertEquals(listOf(103L, 101L), db.playlistDao().observeTracksIn(2L).firstList().map { it.id })

        val reordered = fixtureManifest().let { m ->
            m.copy(playlists = m.playlists.map { if (it.id == 2L) it.copy(trackIds = listOf(101L, 103L)) else it })
        }
        applier.apply(reordered)

        assertEquals(listOf(101L, 103L), db.playlistDao().observeTracksIn(2L).firstList().map { it.id })
    }

    @Test
    fun `generation is recorded on the paired server row`() = runDbTest { db ->
        db.syncDao().replaceServer(pairedServer(lastAppliedGeneration = 0))

        fixedClockApplier(db).apply(fixtureManifest())

        val server = db.syncDao().server()
        assertEquals(42L, server?.lastAppliedGeneration)
        assertEquals(FIXED_NOW, server?.lastSyncAt)
    }

    @Test
    fun `departing podcast leaves with its episodes`() = runDbTest { db ->
        val applier = fixedClockApplier(db)
        applier.apply(fixtureManifest())

        applier.apply(fixtureManifest().copy(podcasts = emptyList(), episodes = emptyList()))

        assertTrue(db.syncDao().allPodcasts().isEmpty())
        assertTrue(db.syncDao().allEpisodes().isEmpty())
    }
}
