# Phase 01: Persistence

> Depends on: phase-00-foundations.md
> Read docs/design-spec/_standards.md and docs/design-spec/sync-protocol.md first.
> Provides: the complete Room data layer in `:core:persistence`. Data layer only; no UI, no networking.

## Goal

A Room database that mirrors the sync manifest exactly (synced tables) and holds everything the phone owns itself (local-state tables), with DAOs, reactive Flows, FTS search, and one transaction API the sync engine will call. The cardinal invariant: **synced tables are replaceable at any time from a manifest; local tables survive every sync.** This mirrors the Mac app's podcast content-versus-state separation and extends it to the whole library.

## Non-goals

- No sync networking (phase 03 calls into this layer).
- No migrations machinery beyond version 1 plus the policy for future versions.
- No MediaStore integration; files are app-private and tracked here only by relative path.

## Outcome shape

```
core/persistence/src/main/kotlin/io/cloudcauldron/bocan/persistence/
  BocanDatabase.kt
  Converters.kt
  entities/{TrackEntity, AlbumEntity, ArtistEntity, PlaylistEntity, PlaylistTrackEntity,
            PodcastEntity, EpisodeEntity, TrackFtsEntity,
            PlayStatsEntity, EpisodeStateEntity, LyricsCacheEntity, SyncServerEntity, ScrobbleQueueEntity}
  daos/{LibraryDao, PlaylistDao, PodcastDao, SearchDao, PlayStatsDao, EpisodeStateDao, SyncDao, ScrobbleDao}
  SyncApplier.kt
  model/  (plain data classes the DAOs return for joined queries)
core/persistence/src/test/kotlin/... (mirrored test tree)
core/persistence/src/test/resources/fixtures/manifest-small.json
```

## Definitions and contracts

### Synced tables (wiped and rewritten by `SyncApplier`)

- `tracks`: `id` (Long, PK, the Mac's id), `title`, `artistId`, `artistName`, `albumArtistId`, `albumArtistName`, `albumId`, `albumName`, `trackNumber`, `trackTotal`, `discNumber`, `discTotal`, `year`, `genre`, `composer`, `bpm`, `durationMs`, `sampleRate`, `bitDepth`, `bitrate`, `channelCount`, `isLossless`, `format`, `size`, `sha256`, `relPath`, `artworkHash`, `lyricsHash`, `rating` (0-100), `loved`, `rgTrackGain`, `rgTrackPeak`, `rgAlbumGain`, `rgAlbumPeak`, `clipSourceTrackId` (nullable), `clipStartMs`, `clipEndMs`, `downloadState` (enum: `pending`, `downloaded`, `failed`), `syncedAt`. Indices on `albumId`, `artistId`, `genre`, `downloadState`, `sha256`.
- `albums`: `id` (PK, Mac's albumId), `name`, `albumArtistName`, `year`, `artworkHash`, `trackCount`. Derived from the manifest's tracks during apply (group by `albumId`, min year, first non-null artworkHash).
- `artists`: `id` (PK), `name`. Derived likewise from `albumArtistId`/`albumArtist`.
- `playlists`: `id` (PK), `name`, `kind` (`manual`/`smart`/`folder`), `parentId`, `sortOrder`, `accentColor`, `artworkHash`.
- `playlist_tracks`: `playlistId`, `position`, `trackId`; PK (`playlistId`, `position`), index on `trackId`.
- `podcasts`: `id` (PK), `title`, `author`, `descriptionHtml`, `artworkHash`, `defaultSpeed`.
- `episodes`: `id` (String PK), `podcastId`, `guid`, `title`, `publishedAt`, `durationMs`, `descriptionHtml`, `relPath`, `size`, `sha256`, `hasChapters`, `downloadState`, `syncedAt`, `seedPositionMs`, `seedPlayState`.
- `tracks_fts`: FTS4 external-content table over `tracks` (`title`, `artistName`, `albumName`, `genre`) with the standard sync triggers Room generates for `@Fts4(contentEntity = TrackEntity::class)`.

### Local tables (never touched by `SyncApplier` except seeding)

- `play_stats`: `trackId` (PK), `playCount`, `skipCount`, `lastPlayedAt`, `playDurationTotalSec`, `skipAfterSeconds`. Rows persist even if the track leaves the sync set (rejoin keeps history); a `pruneOrphanedOlderThan(days)` maintenance DAO method exists but nothing calls it yet.
- `episode_state`: `episodeId` (PK), `playPositionMs`, `playState` (`unplayed`/`inProgress`/`played`), `lastPlayedAt`, `completedAt`, `speedOverride` (nullable). Seeded once from the episode's `seedPositionMs`/`seedPlayState` when the row is first created; after that the Mac's values are ignored (the phone owns its own progress).
- `lyrics_cache`: `trackId` (PK), `lyricsHash`, `kind`, `text`, `fetchedAt`.
- `sync_server`: single row: `serverId`, `serverName`, `certFingerprint`, `certDer` (BLOB), `lastAppliedGeneration`, `lastSyncAt`, `pairedAt`.
- `scrobble_queue`: `id` (autogen PK), `provider`, `payloadJson`, `attempts`, `nextAttemptAt`, `deadLettered`. (Schema only; phase 09 uses it.)

### SyncApplier

The one write path for manifests:

```kotlin
class SyncApplier(private val db: BocanDatabase) {
    data class Plan(
        val tracksToDownload: List<TrackEntity>,      // new or sha256 changed, excluding clips
        val episodesToDownload: List<EpisodeEntity>,
        val artworkHashesNeeded: List<String>,
        val relPathsToDelete: List<String>,           // files departing the sync set
    )
    suspend fun plan(manifest: Manifest): Plan        // pure read + diff
    suspend fun apply(manifest: Manifest): Plan       // the plan, plus the transactional write
    suspend fun markDownloaded(trackIds: List<Long>, episodeIds: List<String>)
}
```

`apply` runs in one `withTransaction`: upsert all synced tables, delete departed rows, rebuild `albums`/`artists`, seed missing `play_stats` and `episode_state` rows, update `sync_server.lastAppliedGeneration`. New and changed rows get `downloadState = pending`; unchanged stay `downloaded`. Clip tracks (`clipSourceTrackId != null`) inherit the source's download state and never appear in `tracksToDownload`.

The `Manifest` DTOs (kotlinx.serialization data classes matching sync-protocol.md section 7 exactly) live in this module under `model/manifest/` so both `:core:sync` and tests share them.

### Read API sketch (DAOs return Flows for anything a screen observes)

- `LibraryDao`: `observeAlbums(sort)`, `observeArtists()`, `observeTracksForAlbum(albumId)`, `observeAllTracks(sort)`, `observeGenres()`, `tracksByIds(ids)`, `observeDownloadCounts()` (pending vs downloaded, for the sync UI).
- `PlaylistDao`: `observePlaylistTree()`, `observeTracksIn(playlistId)` ordered by `position`.
- `PodcastDao`: `observeShows()`, `observeEpisodes(podcastId, sortNewestFirst)`, `observeContinueListening()` (join `episode_state` where `playState = inProgress`, ordered by `lastPlayedAt` desc, limit 20).
- `SearchDao`: `search(query): Flow<SearchResults>` using FTS MATCH with the query sanitised (escape quotes, append `*` for prefix matching).
- `PlayStatsDao`: `recordPlay(trackId, playedSec)`, `recordSkip(trackId, atSec)`, `observeStats(trackId)`.
- `EpisodeStateDao`: `updatePosition(episodeId, ms)`, `markPlayed(episodeId)`, `observeState(episodeId)`.

## Implementation plan

1. Add Room (runtime, ktx, compiler via KSP) and kotlinx.serialization to `:core:persistence`. Room `exportSchema = true` with the schema JSON committed under `core/persistence/schemas/`.
2. Entities and converters (enums as strings, Instant as epoch millis).
3. DAOs with the queries above. Every multi-statement write is `@Transaction`.
4. FTS4 external-content table plus a `SearchDao` test proving prefix search and quote escaping.
5. Manifest DTOs + a parser test against `fixtures/manifest-small.json` (hand-write this fixture to cover: a clip track, a smart playlist, a folder, an episode with seed state, nulls everywhere optional).
6. `SyncApplier.plan` and `apply` with exhaustive tests (see test plan).
7. Wire `BocanDatabase` construction into `AppGraph` (single instance, WAL mode is Room's default journal on API 29+; verify).
8. Extend the Kover verify rule to this module.

## Context7 lookups

- use context7: Room KSP setup, FTS4 external content entity, withTransaction, latest stable version
- use context7: kotlinx.serialization JSON ignoreUnknownKeys and explicit nulls configuration

## Dependencies

Room (+ KSP), kotlinx-serialization-json, kotlinx-coroutines. Test: Robolectric (Room needs a Context; use an in-memory database), Turbine.

## Test plan

### SyncApplier
- Fresh DB + manifest: everything lands, all `downloadState = pending`, albums/artists derived correctly (counts, years, artwork).
- Same manifest re-applied: zero rows change, plan is empty.
- Track sha256 changes: plan lists it for re-download; metadata updated; `play_stats` row untouched.
- Track departs: row deleted, `relPathsToDelete` contains its path, `play_stats` survives.
- Metadata-only change (rating 60 to 80, same sha256): applied, not in download plan.
- Clip tracks: never in `tracksToDownload`; inherit source state; deleting the source track also removes clips (enforce via foreign key or applier logic, test either way).
- Episode seen for the first time: `episode_state` seeded from seed fields. Episode seen again with different Mac position: local state unchanged.
- Playlist reorder in manifest: `playlist_tracks` reflects new positions exactly.

### Search
- Prefix match, multi-term AND, quote and special-character safety (`"don't stop"`, `rock*`), empty query returns empty.

### DAO reactivity
- Turbine: `observeAlbums` emits on applier writes; `observeContinueListening` orders by recency and drops completed episodes.

## Acceptance criteria

- [ ] Schema JSON committed; database version 1 builds and opens.
- [ ] `SyncApplier` passes every case above, including the local-state survival cases.
- [ ] Manifest fixture parses with `ignoreUnknownKeys = true` and round-trips.
- [ ] FTS search proven safe against quoting tricks.
- [ ] All DAOs' observed queries emit reactively, proven with Turbine.
- [ ] Kover floor holds for this module.

## Gotchas

- **Local-state seeding is once, ever.** The seed fields live on the synced `episodes` row, but the applier only copies them when creating a missing `episode_state` row. If you re-seed on every sync, the Mac silently stomps phone listening progress, which is the bug this whole design exists to prevent.
- **Deriving albums must be deterministic** (stable ordering before "first non-null artwork") or tests flake and the UI flickers on every sync.
- **FTS4, not FTS5.** Room supports FTS3/FTS4 natively; do not hand-roll FTS5 virtual tables in v1.
- **Foreign keys and the applier's delete order**: delete `playlist_tracks` before `tracks`, episodes before podcasts, or defer FKs inside the transaction.
- **Do not store absolute paths.** Only `relPath`; the media root can move (user clears storage, backup restore).

## Handoff

Phase 02 and 03 assume: `SyncApplier` with the exact signature above, the manifest DTOs in this module, `sync_server` as the pairing persistence point, and reactive DAOs ready for screens. Phase 04 assumes `play_stats` and `episode_state` write APIs exist.
