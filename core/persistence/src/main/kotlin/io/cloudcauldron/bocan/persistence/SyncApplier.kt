package io.cloudcauldron.bocan.persistence

import androidx.room3.withWriteTransaction
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.daos.SyncDao
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeStateEntity
import io.cloudcauldron.bocan.persistence.entities.PlayStatsEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistTrackEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.model.PlayState
import io.cloudcauldron.bocan.persistence.model.manifest.Manifest
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestEpisode
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestTrack
import java.time.Instant

/**
 * The one write path for manifests. apply() replaces the synced tables in a
 * single write transaction; the local-state tables are only ever seeded for
 * rows that do not exist yet and otherwise survive every sync untouched.
 */
class SyncApplier(private val db: BocanDatabase, private val now: () -> Instant = Instant::now) {
    private val log = AppLog.forCategory(LogCategory.Persistence)

    data class Plan(
        val tracksToDownload: List<TrackEntity>,
        val episodesToDownload: List<EpisodeEntity>,
        val artworkHashesNeeded: List<String>,
        val relPathsToDelete: List<String>
    )

    /** Pure read plus diff: what a sync of this manifest would transfer and delete. */
    suspend fun plan(manifest: Manifest): Plan {
        val dao = db.syncDao()
        return resolve(manifest, dao.allTracks(), dao.allEpisodes(), dao.knownArtworkHashes(), now()).plan
    }

    /** The plan, plus the transactional write that makes the manifest current. */
    suspend fun apply(manifest: Manifest): Plan {
        val syncedAt = now()
        log.debug(
            "syncApply.start",
            mapOf(
                "generation" to manifest.generation,
                "tracks" to manifest.tracks.size,
                "playlists" to manifest.playlists.size,
                "podcasts" to manifest.podcasts.size,
                "episodes" to manifest.episodes.size
            )
        )
        val plan = db.withWriteTransaction {
            val dao = db.syncDao()
            val resolution = resolve(manifest, dao.allTracks(), dao.allEpisodes(), dao.knownArtworkHashes(), syncedAt)
            writeSyncedTables(dao, manifest, resolution)
            seedLocalState(dao, manifest, resolution)
            dao.recordApplied(manifest.generation, syncedAt)
            resolution.plan
        }
        log.debug(
            "syncApply.end",
            mapOf(
                "generation" to manifest.generation,
                "tracksToDownload" to plan.tracksToDownload.size,
                "episodesToDownload" to plan.episodesToDownload.size,
                "artworkNeeded" to plan.artworkHashesNeeded.size,
                "pathsToDelete" to plan.relPathsToDelete.size
            )
        )
        return plan
    }

    /** Flip rows to downloaded once their bytes are verified on disk. Clips follow their source. */
    suspend fun markDownloaded(trackIds: List<Long>, episodeIds: List<String>) {
        db.withWriteTransaction {
            val dao = db.syncDao()
            trackIds.chunked(CHUNK).forEach { dao.setTrackDownloadState(it, DownloadState.Downloaded) }
            episodeIds.chunked(CHUNK).forEach { dao.setEpisodeDownloadState(it, DownloadState.Downloaded) }
        }
    }

    private data class Resolution(
        val plan: Plan,
        val trackEntities: List<TrackEntity>,
        val episodeEntities: List<EpisodeEntity>,
        val departedTrackIds: List<Long>,
        val departedEpisodeIds: List<String>,
        val albums: List<AlbumEntity>,
        val artists: List<ArtistEntity>
    )

    private fun resolve(
        manifest: Manifest,
        existingTracks: List<TrackEntity>,
        existingEpisodes: List<EpisodeEntity>,
        knownArtworkHashes: List<String>,
        syncedAt: Instant
    ): Resolution {
        val manifestTracks = sanitizedTracks(manifest)
        val existingById = existingTracks.associateBy { it.id }
        val trackStates = resolveTrackStates(manifestTracks, existingById)
        val trackEntities = manifestTracks.map { toTrackEntity(it, trackStates.getValue(it.id), syncedAt) }

        val existingEpisodesById = existingEpisodes.associateBy { it.id }
        val episodeEntities = manifest.episodes.map { toEpisodeEntity(it, existingEpisodesById[it.id], syncedAt) }

        val manifestTrackIds = manifestTracks.mapTo(mutableSetOf()) { it.id }
        val manifestEpisodeIds = manifest.episodes.mapTo(mutableSetOf()) { it.id }

        val plan = Plan(
            tracksToDownload = trackEntities.filter { track ->
                track.clipSourceTrackId == null &&
                    existingById[track.id]?.sha256 != track.sha256
            },
            episodesToDownload = episodeEntities.filter { episode ->
                existingEpisodesById[episode.id]?.sha256 != episode.sha256
            },
            artworkHashesNeeded = artworkHashesNeeded(manifest, knownArtworkHashes),
            relPathsToDelete = relPathsToDelete(manifestTracks, manifest.episodes, existingTracks, existingEpisodes)
        )
        return Resolution(
            plan = plan,
            trackEntities = trackEntities,
            episodeEntities = episodeEntities,
            departedTrackIds = existingTracks.map { it.id }.filterNot { it in manifestTrackIds },
            departedEpisodeIds = existingEpisodes.map { it.id }.filterNot { it in manifestEpisodeIds },
            albums = deriveAlbums(manifestTracks),
            artists = deriveArtists(manifestTracks)
        )
    }

    /** Manifest tracks minus clips whose source is not in the same manifest. */
    private fun sanitizedTracks(manifest: Manifest): List<ManifestTrack> {
        val ids = manifest.tracks.mapTo(mutableSetOf()) { it.id }
        val (kept, dropped) = manifest.tracks.partition { it.clip == null || it.clip.sourceTrackId in ids }
        if (dropped.isNotEmpty()) {
            log.warning("syncApply.orphanClipsDropped", mapOf("ids" to dropped.map { it.id }))
        }
        return kept
    }

    /**
     * New and sha256-changed rows become pending; unchanged rows keep their
     * current state. Clips always inherit their source track's state.
     */
    private fun resolveTrackStates(manifestTracks: List<ManifestTrack>, existingById: Map<Long, TrackEntity>): Map<Long, DownloadState> {
        val states = mutableMapOf<Long, DownloadState>()
        val (clips, sources) = manifestTracks.partition { it.clip != null }
        sources.forEach { track ->
            val existing = existingById[track.id]
            states[track.id] =
                if (existing != null && existing.sha256 == track.sha256) existing.downloadState else DownloadState.Pending
        }
        clips.forEach { clipTrack ->
            states[clipTrack.id] = states.getValue(clipTrack.clip!!.sourceTrackId)
        }
        return states
    }

    private fun artworkHashesNeeded(manifest: Manifest, knownArtworkHashes: List<String>): List<String> {
        val known = knownArtworkHashes.toSet()
        val referenced = buildSet {
            manifest.tracks.forEach { it.artworkHash?.let(::add) }
            manifest.playlists.forEach { it.artworkHash?.let(::add) }
            manifest.podcasts.forEach { it.artworkHash?.let(::add) }
        }
        return (referenced - known).sorted()
    }

    private fun relPathsToDelete(
        manifestTracks: List<ManifestTrack>,
        manifestEpisodes: List<ManifestEpisode>,
        existingTracks: List<TrackEntity>,
        existingEpisodes: List<EpisodeEntity>
    ): List<String> {
        val keptPaths = buildSet {
            manifestTracks.forEach { add(it.relPath) }
            manifestEpisodes.forEach { add(it.relPath) }
        }
        val localPaths = buildSet {
            existingTracks.forEach { add(it.relPath) }
            existingEpisodes.forEach { add(it.relPath) }
        }
        return (localPaths - keptPaths).sorted()
    }

    private suspend fun writeSyncedTables(dao: SyncDao, manifest: Manifest, resolution: Resolution) {
        // Membership rows go first so no departing track is still referenced.
        dao.clearPlaylistTracks()
        dao.clearPlaylists()

        resolution.trackEntities.chunked(CHUNK).forEach { dao.upsertTracks(it) }
        resolution.departedTrackIds.chunked(CHUNK).forEach { dao.deleteTracks(it) }

        dao.clearAlbums()
        resolution.albums.chunked(CHUNK).forEach { dao.insertAlbums(it) }
        dao.clearArtists()
        resolution.artists.chunked(CHUNK).forEach { dao.insertArtists(it) }

        dao.insertPlaylists(manifest.playlists.map(::toPlaylistEntity))
        val membership = manifest.playlists.flatMap { playlist ->
            playlist.trackIds.mapIndexed { position, trackId ->
                PlaylistTrackEntity(playlistId = playlist.id, position = position, trackId = trackId)
            }
        }
        membership.chunked(CHUNK).forEach { dao.insertPlaylistTracks(it) }

        // Episodes before podcasts on the way out.
        resolution.episodeEntities.chunked(CHUNK).forEach { dao.upsertEpisodes(it) }
        resolution.departedEpisodeIds.chunked(CHUNK).forEach { dao.deleteEpisodes(it) }
        val manifestPodcastIds = manifest.podcasts.mapTo(mutableSetOf()) { it.id }
        dao.upsertPodcasts(manifest.podcasts.map(::toPodcastEntity))
        dao.allPodcasts().map { it.id }.filterNot { it in manifestPodcastIds }
            .chunked(CHUNK).forEach { dao.deletePodcasts(it) }
    }

    /** Insert-if-missing only: existing local state is never overwritten. */
    private suspend fun seedLocalState(dao: SyncDao, manifest: Manifest, resolution: Resolution) {
        resolution.trackEntities
            .map { PlayStatsEntity(trackId = it.id) }
            .chunked(CHUNK)
            .forEach { dao.seedPlayStats(it) }
        manifest.episodes
            .map { episode ->
                EpisodeStateEntity(
                    episodeId = episode.id,
                    playPositionMs = episode.playPositionMs,
                    playState = PlayState.fromWireOrNull(episode.playState) ?: PlayState.Unplayed
                )
            }
            .chunked(CHUNK)
            .forEach { dao.seedEpisodeStates(it) }
    }

    private companion object {
        /** Keep bulk statements far below SQLite's bind-variable ceiling. */
        const val CHUNK = 500
    }
}
