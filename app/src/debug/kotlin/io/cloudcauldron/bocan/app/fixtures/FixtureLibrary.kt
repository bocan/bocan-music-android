package io.cloudcauldron.bocan.app.fixtures

import android.content.Context
import io.cloudcauldron.bocan.app.demo.DemoAssetCorruptException
import io.cloudcauldron.bocan.app.demo.DemoAssets
import io.cloudcauldron.bocan.app.demo.DemoFiles
import io.cloudcauldron.bocan.app.demo.DemoLibrary
import io.cloudcauldron.bocan.app.demo.artworkHashes
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.entities.LyricsCacheEntity
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.model.LyricsKind
import io.cloudcauldron.bocan.persistence.model.manifest.Manifest
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestCodec
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.net.TrustStore
import java.io.InputStream
import java.time.Instant
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The end-to-end fixture library (phase 17), debug builds only. It loads a library
 * big enough to reach every surface with no Mac, through the same path as the
 * phase 14 demo: files are copied under the media root and verified against the
 * manifest's sha256, rows go in through [io.cloudcauldron.bocan.persistence.SyncApplier],
 * and lyrics land in the cache. The paired server row goes in through [TrustStore],
 * the pairing write path, with a fingerprint no real Mac can present, so the
 * "Mac" is always unreachable.
 *
 * [seed] is idempotent and wipes nothing: the harness owns wiping.
 */
class FixtureLibrary(
    private val assets: DemoAssets,
    private val store: DemoLibrary.Store,
    private val dispatchers: CoroutineDispatchers,
    private val log: AppLog = AppLog.forCategory(LogCategory.App),
    private val now: () -> Instant = Instant::now
) {
    private val trustStore = TrustStore(store.database.syncDao())

    suspend fun seed(profile: FixtureProfile) = withContext(dispatchers.io) {
        when (profile) {
            // The phase 14 path, untouched: the app seeds the demo on its first Home.
            FixtureProfile.UnpairedDemo -> Unit
            FixtureProfile.PairedLibrary -> {
                pair()
                seedLibrary()
            }
            FixtureProfile.EmptyPaired -> pair()
            // Nothing to seed: the harness spends the demo's seed, and a sync fills the library.
            FixtureProfile.Unpaired -> Unit
        }
        log.info("fixture.seeded", mapOf("profile" to profile.name))
    }

    private suspend fun pair() {
        trustStore.save(
            SyncServerEntity(
                serverId = SERVER_ID,
                serverName = SERVER_NAME,
                certFingerprint = UNREACHABLE_FINGERPRINT,
                certDer = ByteArray(0),
                lastAppliedGeneration = 0,
                lastSyncAt = null,
                pairedAt = now()
            )
        )
    }

    private suspend fun seedLibrary() {
        val manifest = ManifestCodec.decode(read(MANIFEST_PATH).decodeToString())
        // A CUE clip shares its source's relPath and bytes: copy each file once.
        manifest.tracks.distinctBy { it.relPath }.forEach { track ->
            DemoFiles.copyVerified(assets, "$LIBRARY_DIR/${track.relPath}", store.mediaLayout.trackFile(track.relPath), track.sha256)
        }
        manifest.episodes.forEach { episode ->
            DemoFiles.copyVerified(assets, episode.relPath, store.mediaLayout.episodeFile(episode.relPath), episode.sha256)
        }
        manifest.artworkHashes().forEach { hash ->
            DemoFiles.copyVerified(assets, "$ARTWORK_DIR/$hash", store.artworkStore.fileFor(hash), hash)
        }

        store.applier.apply(manifest)
        store.applier.markDownloaded(manifest.tracks.map { it.id }, manifest.episodes.map { it.id })
        seedLyrics(manifest)
    }

    private suspend fun seedLyrics(manifest: Manifest) {
        val kinds = Json.decodeFromString<Map<String, String>>(read(LYRICS_KINDS_PATH).decodeToString())
        manifest.tracks.forEach { track ->
            val hash = track.lyricsHash ?: return@forEach
            val kind = LyricsKind.fromWire(kinds.getValue(track.id.toString()))
            val path = "$LYRICS_DIR/${track.id}.${if (kind == LyricsKind.Synced) "lrc" else "txt"}"
            val bytes = read(path)
            if (DemoFiles.sha256Hex(bytes) != hash) throw DemoAssetCorruptException(path)
            store.database.lyricsDao().upsert(LyricsCacheEntity(track.id, hash, kind, bytes.decodeToString(), now()))
        }
    }

    private fun read(path: String): ByteArray = assets.open(path).use(InputStream::readBytes)

    companion object {
        /** Every fixture id is at or above this; the demo's ids sit one million below. */
        const val ID_BASE = DemoLibrary.ID_BASE + 1_000_000L

        const val SERVER_ID = "fixture"
        const val SERVER_NAME = "Fixture Mac"

        /** No certificate hashes to this, so pinning rejects every peer and the Mac stays unreachable. */
        val UNREACHABLE_FINGERPRINT: String = "00".repeat(32)

        private const val MANIFEST_PATH = "manifest.json"
        private const val LIBRARY_DIR = "library"
        private const val ARTWORK_DIR = "artwork"
        private const val LYRICS_DIR = "lyrics"
        private const val LYRICS_KINDS_PATH = "lyrics/kinds.json"
    }
}

/** The APK's `assets/fixture/` tree, debug builds only. */
class AndroidFixtureAssets(private val context: Context) : DemoAssets {
    override fun open(path: String): InputStream = context.assets.open("$ROOT/$path")

    private companion object {
        const val ROOT = "fixture"
    }
}
