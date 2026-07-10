# Phase 03: Sync Engine

> Depends on: phase-01-persistence.md, phase-02-discovery-pairing.md
> Read docs/design-spec/_standards.md and docs/design-spec/sync-protocol.md first. This phase implements protocol sections 6 through 9 (client side).
> Provides: the full one-way sync pipeline in `:core:sync`, auto-sync triggers, and the sync status surface in `:app`.

## Goal

When the phone sees its paired Mac, the library flows down and converges: manifest fetched and diffed, files downloaded resumably and verified, departures deleted, database flipped in one transaction. The user sees progress, can trigger a sync by hand, and can trust that pulling the plug mid-sync never corrupts anything.

## Non-goals

- Nothing is uploaded, ever. The phone sends only HTTP requests.
- No cellular sync in v1 (the Mac is on a LAN by definition; still, gate on unmetered network explicitly).
- No partial-library selection UI on the phone; the sync set is chosen on the Mac.

## Outcome shape

```
core/sync/src/main/kotlin/io/cloudcauldron/bocan/sync/
  engine/SyncEngine.kt          // orchestrator state machine
  engine/SyncState.kt           // sealed UI-consumable state
  engine/Downloader.kt          // one file: range resume, sha256 verify, atomic rename
  engine/MediaLayout.kt         // relPath -> File mapping under getExternalFilesDir
  engine/ArtworkStore.kt        // content-addressed artwork files + lookup
  auto/SyncTriggers.kt          // discovery-driven + periodic WorkManager triggers
  auto/SyncWorker.kt
  service/SyncForegroundService.kt
app/src/main/kotlin/io/cloudcauldron/bocan/app/sync/
  SyncStatusScreen.kt  SyncStatusViewModel.kt  SyncBanner.kt
```

## Definitions and contracts

```kotlin
sealed interface SyncState {
    data object Idle : SyncState
    data object CheckingManifest : SyncState
    data class Transferring(
        val filesDone: Int, val filesTotal: Int,
        val bytesDone: Long, val bytesTotal: Long,
        val currentItem: String,
    ) : SyncState
    data class Applying(val phase: String) : SyncState
    data class Done(val at: Instant, val downloaded: Int, val deleted: Int, val failures: List<ItemFailure>) : SyncState
    data class Failed(val error: SyncError) : SyncState
    data object ServerUnreachable : SyncState   // paused mid-transfer, will resume on rediscovery
}

class SyncEngine(...) {
    val state: StateFlow<SyncState>
    suspend fun syncNow(force: Boolean = false)   // force ignores the generation short-circuit
    fun cancel()
}
```

`MediaLayout`: media root is `context.getExternalFilesDir(null)/media`. Tracks live at `media/library/<relPath>`, episodes at `media/<relPath>` (episode relPaths already start with `Podcasts/`), artwork at `media/artwork/<hash>.<ext>`. `relPath` is validated defensively (reject `..`, leading `/`, empty segments) even though the server sanitizes; a hostile manifest must not escape the media root.

`Downloader.download(url, expectedSha256, target)`:
1. If `<target>.part` exists, request `Range: bytes=<partSize>-` and append; else full GET.
2. Send `If-Match: <expectedSha256>`; on `412`, throw `SyncError.ManifestStale` (engine refetches manifest and restarts the plan).
3. Stream to the `.part` file, updating a progress callback and a running SHA-256 digest (for resumed files, re-digest the existing prefix first).
4. On completion compare digests; mismatch deletes the `.part` and retries once from scratch, then records `ItemFailure`.
5. `Files.move(..., ATOMIC_MOVE)` into place.

## Implementation plan

1. `MediaLayout` + path validation tests.
2. `Downloader` with MockWebServer tests (see test plan). Read timeout generous (120 s), but a stall detector: no bytes for 30 s aborts with a resumable state.
3. `SyncEngine` orchestration, exactly protocol section 9:
   ping -> generation check -> manifest -> `SyncApplier.plan` -> transfer queue (artwork, then tracks ordered by album, then episodes) -> `SyncApplier.apply` -> `markDownloaded` per completed file (batched every 50 files so an interrupted sync keeps its progress) -> post-commit deletes -> prune empty dirs.
   Concurrency: 3 parallel downloads via a `Semaphore`; single-flight guard so overlapping `syncNow` calls coalesce.
   Note the deliberate ordering difference from a naive reading of the protocol: files are transferred BEFORE the DB apply so the library never references audio that is not yet on disk; the plan comes from `SyncApplier.plan` (read-only), and `apply` runs after transfers succeed. Items that failed to download stay `pending` and are retried next sync.
4. `SyncForegroundService`: started for active transfers, `dataSync` foreground service type, progress notification (indeterminate during manifest, determinate during transfer, with a Cancel action). Stops itself when the engine returns to Idle/Done.
5. `SyncTriggers`:
   - Discovery-driven: while the app process is alive, `MacDiscovery` emitting the paired Mac triggers `syncNow()` (debounced, at most once per 15 minutes unless generation changed).
   - Periodic: a WorkManager `PeriodicWorkRequest` (6 h, unmetered network constraint) that checks discovery for up to 20 s, pings, and syncs if the generation moved. The worker delegates to the same `SyncEngine`.
   - Settings toggles: auto-sync on/off, sync only while charging (WorkManager constraint).
6. Sync UI: a status screen (last sync time, generation, item counts, per-item failures list, Sync Now button, storage used) and a small `SyncBanner` composable for the library screen while transferring. All strings in `strings.xml`.
7. Storage accounting: sum of `media/` reported on the status screen; warn when device free space is under 2x the pending transfer size and pause before starting transfers that cannot fit.

## Context7 lookups

- use context7: WorkManager PeriodicWorkRequest constraints unmetered charging, latest API
- use context7: Android foreground service types dataSync requirements Android 14 and 15
- use context7: OkHttp streaming response body with progress and Range requests

## Dependencies

WorkManager. Everything else already present.

## Test plan

### Downloader (MockWebServer)
- Full download, digest verified, atomic rename observed (no `.part` left).
- Interrupted at 50 percent (server closes socket): `.part` retained; second call sends correct `Range` header and completes; final digest correct.
- Corrupt body (server sends wrong bytes): one retry, then `ItemFailure`, no file in place.
- `412` response surfaces `ManifestStale`.
- Stall (server hangs): abort within the stall window, state resumable.

### Engine
- Fake server fixture (MockWebServer dispatcher serving `manifest-small.json` + files): fresh sync end to end; DB rows `downloaded`; files on disk byte-identical; empty second sync short-circuits on generation.
- Mid-sync cancel: engine stops within one file, next `syncNow` resumes without re-downloading completed files.
- Departed track: file deleted only after apply commits.
- Insufficient space: engine refuses to start transfers, state explains.
- Path traversal manifest (`relPath: "../../evil"`): rejected, sync fails safe.

### Triggers
- Discovery emission triggers exactly one engine run (debounce proven with a test dispatcher).

## Acceptance criteria

- [ ] End-to-end sync against the fixture server converges: files, DB, artwork, deletions.
- [ ] Kill the process mid-transfer, relaunch, re-sync: converges with no re-download of completed files and no corruption.
- [ ] Resume uses Range requests, proven by recorded requests.
- [ ] Foreground service shows determinate progress and stops when done; no lingering notification.
- [ ] Auto-sync fires when the paired Mac appears on Wi-Fi and respects the user toggles.
- [ ] A departed file is deleted from disk and DB; its play stats survive.
- [ ] Path traversal and disk-full paths fail safe, with tests.
- [ ] Kover floor holds for `:core:sync`.

## Gotchas

- **Transfer before apply.** If you apply the manifest first, the UI shows ghost tracks that cannot play. The plan/transfer/apply split in the implementation plan is deliberate; keep it.
- **Resumed digests**: SHA-256 of a resumed file must include the bytes already on disk. Re-hash the prefix on resume; do not persist digest state.
- **Doze and WorkManager**: the periodic worker will not run exactly every 6 h; that is fine, discovery-driven sync is the primary path. Do not add `setExactAndAllowWhileIdle` alarms.
- **`getExternalFilesDir` can be null** (storage unmounted); every entry point checks and surfaces a typed error instead of NPEing.
- **Generation is not a hash.** Equal generation means skip; unequal means fetch manifest and let the differ decide. Never infer specific changes from the generation delta.
- **Clip tracks** never enter the download queue (phase 01 guarantees the plan excludes them); if you see one there, the bug is in the applier, not here.

## Handoff

Phase 04 assumes: audio files present under `MediaLayout` paths with `downloadState = downloaded`, `ArtworkStore.fileFor(hash)`, and `SyncState` observable for UI. Phase 05 assumes the status screen route and `SyncBanner` exist.
