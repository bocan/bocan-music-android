package io.cloudcauldron.bocan.persistence

import androidx.test.core.app.ApplicationProvider
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.model.manifest.Manifest
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestClip
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestCodec
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestEpisode
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestPlaylist
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestPodcast
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestTrack
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** The frozen clock every persistence test runs on. */
val FIXED_NOW: Instant = Instant.parse("2026-07-10T12:00:00Z")

/** Run a test body against a fresh in-memory database on the bundled driver. */
fun runDbTest(block: suspend TestScope.(BocanDatabase) -> Unit) = runTest {
    val db = BocanDatabase.createInMemory(
        ApplicationProvider.getApplicationContext(),
        StandardTestDispatcher(testScheduler)
    )
    try {
        block(db)
    } finally {
        db.close()
    }
}

fun fixedClockApplier(db: BocanDatabase): SyncApplier = SyncApplier(db) { FIXED_NOW }

/** First emission of an observed list query. */
suspend fun <T> Flow<List<T>>.firstList(): List<T> = first()

fun pairedServer(lastAppliedGeneration: Long = 0): SyncServerEntity = SyncServerEntity(
    serverId = "test-server",
    serverName = "Test Mac",
    certFingerprint = "ab".repeat(32),
    certDer = byteArrayOf(1, 2, 3),
    lastAppliedGeneration = lastAppliedGeneration,
    lastSyncAt = null,
    pairedAt = FIXED_NOW
)

fun readFixture(name: String): String = checkNotNull(object {}.javaClass.classLoader?.getResource("fixtures/$name")) {
    "Missing test fixture: $name"
}.readText()

fun fixtureManifest(): Manifest = ManifestCodec.decode(readFixture("manifest-small.json"))

/** Compact builders for hand-rolled manifests. */
object Manifests {
    fun manifest(
        generation: Long = 1,
        tracks: List<ManifestTrack> = emptyList(),
        playlists: List<ManifestPlaylist> = emptyList(),
        podcasts: List<ManifestPodcast> = emptyList(),
        episodes: List<ManifestEpisode> = emptyList()
    ): Manifest = Manifest(
        protocolVersion = 1,
        serverId = "test-server",
        serverName = "Test Mac",
        generation = generation,
        generatedAt = "2026-07-10T12:00:00Z",
        tracks = tracks,
        playlists = playlists,
        podcasts = podcasts,
        episodes = episodes
    )

    fun track(
        id: Long,
        title: String = "Track $id",
        sha256: String = "sha-$id",
        relPath: String = "Artist/Album/$id.flac",
        artistId: Long = 1,
        artist: String = "Artist $artistId",
        albumId: Long = 1,
        album: String = "Album $albumId",
        year: Int? = null,
        genre: String? = null,
        rating: Int = 0,
        trackNumber: Int? = null,
        artworkHash: String? = null,
        clip: ManifestClip? = null
    ): ManifestTrack = ManifestTrack(
        id = id,
        relPath = relPath,
        size = 1000,
        sha256 = sha256,
        format = "flac",
        durationMs = 60_000,
        title = title,
        artist = artist,
        artistId = artistId,
        albumArtist = artist,
        albumArtistId = artistId,
        album = album,
        albumId = albumId,
        trackNumber = trackNumber,
        year = year,
        genre = genre,
        rating = rating,
        isLossless = true,
        artworkHash = artworkHash,
        clip = clip
    )

    fun episode(
        id: String,
        podcastId: Long = 4,
        sha256: String = "sha-$id",
        playPositionMs: Long = 0,
        playState: String = "unplayed",
        publishedAt: String = "2026-06-01T09:00:00Z"
    ): ManifestEpisode = ManifestEpisode(
        id = id,
        podcastId = podcastId,
        guid = "guid-$id",
        title = "Episode $id",
        publishedAt = publishedAt,
        durationMs = 3_600_000,
        relPath = "Podcasts/$podcastId/$id.mp3",
        size = 5000,
        sha256 = sha256,
        playPositionMs = playPositionMs,
        playState = playState
    )

    fun podcast(id: Long = 4, title: String = "Show $id"): ManifestPodcast = ManifestPodcast(id = id, title = title)
}
