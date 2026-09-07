package io.cloudcauldron.bocan.app.demo

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.persistence.SyncApplier
import io.cloudcauldron.bocan.persistence.entities.LyricsCacheEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.LyricsKind
import io.cloudcauldron.bocan.persistence.model.manifest.Manifest
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestCodec
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.engine.ArtworkStore
import io.cloudcauldron.bocan.sync.engine.MediaLayout
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What one [DemoLibrary.seed] call did. */
sealed interface SeedResult {
    data object Seeded : SeedResult

    data object AlreadyActive : SeedResult

    data object NotEligible : SeedResult

    data class Failed(val error: Throwable) : SeedResult
}

/** A demo asset did not hash to what the bundled manifest promised. */
class DemoAssetCorruptException(path: String) : IOException("demo asset $path does not match its manifest sha256")

/**
 * The two-track demo album bundled in the APK (phase 14). It exists so a fresh
 * install with no Mac still has music: Play review, a first look, a phone that
 * never gets paired.
 *
 * The demo is ordinary content, not a mode. Its files are copied under the
 * media root exactly where a sync would put them and its rows go in through
 * [SyncApplier.apply], so the library, the player, artwork, Auto, and the
 * departure path treat it like anything the Mac served. The first real
 * manifest lists its relPaths as departed and the engine deletes them.
 *
 * Every demo id is at or above [ID_BASE], far past any Mac row id, so the
 * phone-local tables keyed by track id (play stats, lyrics cache) can never
 * attach demo history to a real track. Every demo relPath starts with
 * [REL_PATH_PREFIX]; "active" is derived from that plus the missing paired
 * server row, so there is no separate demo state to drift.
 */
class DemoLibrary(
    private val assets: DemoAssets,
    private val store: Store,
    private val prefs: DemoPreferencesSource,
    private val dispatchers: CoroutineDispatchers,
    private val log: AppLog = AppLog.forCategory(LogCategory.App),
    private val now: () -> Instant = Instant::now
) {
    /** Where the demo lands: the database via its one write path, and the media root. */
    class Store(val database: BocanDatabase, val applier: SyncApplier, val mediaLayout: MediaLayout, val artworkStore: ArtworkStore)

    private val seedingFlow = MutableStateFlow(false)
    private val mutex = Mutex()

    /** True while a seed is copying files and writing rows; the library shows a loading state. */
    val seeding: StateFlow<Boolean> = seedingFlow.asStateFlow()

    /** True when no Mac is paired and the tracks and episodes tables are both empty. */
    suspend fun isEligible(): Boolean = withContext(dispatchers.io) {
        val dao = store.database.syncDao()
        dao.server() == null && dao.allTracks().isEmpty() && dao.allEpisodes().isEmpty()
    }

    /**
     * True when no Mac is paired, something is in the library, and every track and
     * episode is a demo one. A half-and-half library (a real sync happened, then the
     * user unpaired) is never "the demo".
     */
    suspend fun isActive(): Boolean = withContext(dispatchers.io) {
        val dao = store.database.syncDao()
        if (dao.server() != null) return@withContext false
        val tracks = dao.allTracks()
        val episodes = dao.allEpisodes()
        (tracks.isNotEmpty() || episodes.isNotEmpty()) &&
            tracks.all { it.relPath.startsWith(REL_PATH_PREFIX) } &&
            episodes.all { it.relPath.startsWith(EPISODE_REL_PATH_PREFIX) }
    }

    /**
     * Chapters for a demo episode, straight from the assets, or null for any other id so
     * the caller falls through to the Mac. The demo is the only source of chapters an
     * unpaired phone can have.
     */
    suspend fun chaptersJson(episodeId: String): String? = withContext(dispatchers.io) {
        if (!episodeId.startsWith(EPISODE_ID_PREFIX)) return@withContext null
        try {
            assets.open("$CHAPTERS_DIR/$episodeId.json").use { it.readBytes().decodeToString() }
        } catch (missing: IOException) {
            log.warning("demo.chapters.missing", mapOf("episodeId" to episodeId, "error" to missing.toString()))
            null
        }
    }

    /**
     * [isActive] as a flow, on the tracks table alone (the demo always ships tracks);
     * a paired phone short-circuits without reading it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActive(): Flow<Boolean> = store.database.syncDao().observeServer().flatMapLatest { server ->
        if (server != null) flowOf(false) else store.database.libraryDao().observeAllTracksByTitle().map { it.isAllDemo() }
    }

    /**
     * The one automatic seed per install. Runs only while eligible and the flag
     * is unset; sets the flag afterwards whatever happened, so a broken asset
     * can never loop and a paired phone never checks again.
     */
    suspend fun seedOnFirstLaunch() {
        if (prefs.autoSeeded.first()) return
        if (isEligible()) {
            val result = seed()
            log.info("demo.autoSeed", mapOf("result" to result.toString()))
        }
        prefs.setAutoSeeded()
    }

    /**
     * Seed now if eligible. Files land first (copied to a `.part`, hashed,
     * renamed), rows last, so an interruption leaves stray files and no rows,
     * and the next seed overwrites the files. Idempotent while active.
     */
    suspend fun seed(): SeedResult = mutex.withLock {
        withContext(dispatchers.io) {
            when {
                isActive() -> SeedResult.AlreadyActive
                !isEligible() -> SeedResult.NotEligible
                else -> seedNow()
            }
        }
    }

    /**
     * Remove the demo if it is active: an empty manifest with the demo's own
     * server identity clears the synced tables, then the departed files, the
     * covers, and the cached lyrics go, and empty folders are pruned.
     */
    suspend fun clear() = mutex.withLock {
        withContext(dispatchers.io) {
            if (!isActive()) return@withContext
            val dao = store.database.syncDao()
            val tracks = dao.allTracks()
            val playlists = store.database.playlistDao().observePlaylistTree().first()
            val podcasts = dao.allPodcasts()
            val artworkHashes = buildSet {
                tracks.forEach { track -> track.artworkHash?.let(::add) }
                playlists.forEach { playlist -> playlist.artworkHash?.let(::add) }
                podcasts.forEach { podcast -> podcast.artworkHash?.let(::add) }
            }

            val plan = store.applier.apply(emptyManifest())
            plan.relPathsToDelete.forEach { relPath -> DemoFiles.deleteQuietly(store.mediaLayout.fileForRelPath(relPath), log) }
            artworkHashes.forEach { hash -> DemoFiles.deleteQuietly(store.artworkStore.fileFor(hash), log) }
            tracks.forEach { store.database.lyricsDao().delete(it.id) }
            store.mediaLayout.pruneEmptyDirs()
            log.info("demo.cleared", mapOf("tracks" to tracks.size))
        }
    }

    private suspend fun seedNow(): SeedResult {
        seedingFlow.value = true
        val started = now()
        return try {
            val manifest = ManifestCodec.decode(assets.open(MANIFEST_PATH).use { it.readBytes().decodeToString() })
            manifest.tracks.forEach { track ->
                DemoFiles.copyVerified(assets, "$LIBRARY_DIR/${track.relPath}", store.mediaLayout.trackFile(track.relPath), track.sha256)
            }
            // Episode relPaths already start with Podcasts/, and the assets mirror the media root.
            manifest.episodes.forEach { episode ->
                DemoFiles.copyVerified(assets, episode.relPath, store.mediaLayout.episodeFile(episode.relPath), episode.sha256)
            }
            manifest.artworkHashes().forEach { hash ->
                DemoFiles.copyVerified(assets, "$ARTWORK_DIR/$hash", store.artworkStore.fileFor(hash), hash)
            }

            store.applier.apply(manifest)
            store.applier.markDownloaded(manifest.tracks.map { it.id }, manifest.episodes.map { it.id })
            seedLyrics(manifest)

            log.info("demo.seeded", mapOf("tracks" to manifest.tracks.size, "ms" to (now().toEpochMilli() - started.toEpochMilli())))
            SeedResult.Seeded
        } catch (unavailable: SyncError) {
            log.warning("demo.seed.failed", mapOf("error" to unavailable.toString()))
            SeedResult.Failed(unavailable)
        } catch (io: IOException) {
            log.warning("demo.seed.failed", mapOf("error" to io.toString()))
            SeedResult.Failed(io)
        } finally {
            seedingFlow.value = false
        }
    }

    private suspend fun seedLyrics(manifest: Manifest) {
        manifest.tracks.forEach { track ->
            val hash = track.lyricsHash ?: return@forEach
            val path = "$LYRICS_DIR/${track.id}.lrc"
            val bytes = assets.open(path).use { it.readBytes() }
            if (DemoFiles.sha256Hex(bytes) != hash) throw DemoAssetCorruptException(path)
            store.database.lyricsDao().upsert(LyricsCacheEntity(track.id, hash, LyricsKind.Synced, bytes.decodeToString(), now()))
        }
    }

    private fun emptyManifest(): Manifest = Manifest(
        protocolVersion = PROTOCOL_VERSION,
        serverId = SERVER_ID,
        serverName = SERVER_NAME,
        generation = 0,
        generatedAt = now().toString()
    )

    companion object {
        /** Every demo id (track, artist, album, playlist) is this plus a small number. */
        const val ID_BASE = 9_000_000_000L

        /** Every demo track relPath starts with this folder. */
        const val REL_PATH_PREFIX = "Demo/"

        /** Every demo episode relPath starts with this folder (episode paths always start with Podcasts/). */
        const val EPISODE_REL_PATH_PREFIX = "Podcasts/Demo/"

        /** Every demo episode id starts with this; Mac episode ids are content hashes and cannot. */
        const val EPISODE_ID_PREFIX = "demo-episode-"

        fun isDemoTrackId(id: Long): Boolean = id >= ID_BASE

        private const val MANIFEST_PATH = "manifest.json"
        private const val LIBRARY_DIR = "library"
        private const val ARTWORK_DIR = "artwork"
        private const val LYRICS_DIR = "lyrics"
        private const val CHAPTERS_DIR = "chapters"
        private const val PROTOCOL_VERSION = 1
        private const val SERVER_ID = "demo"
        private const val SERVER_NAME = "Demo library"
    }
}

private fun List<TrackEntity>.isAllDemo(): Boolean = isNotEmpty() && all { it.relPath.startsWith(DemoLibrary.REL_PATH_PREFIX) }

private fun Manifest.artworkHashes(): Set<String> = buildSet {
    tracks.forEach { track -> track.artworkHash?.let(::add) }
    playlists.forEach { playlist -> playlist.artworkHash?.let(::add) }
    podcasts.forEach { podcast -> podcast.artworkHash?.let(::add) }
}
