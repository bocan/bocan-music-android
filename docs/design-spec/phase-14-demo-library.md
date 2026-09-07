# Phase 14: Built-in Demo Library

> Depends on: phase-03-sync-engine.md, phase-04-playback-engine.md, phase-05-library-ui.md, phase-06-now-playing-queue.md, phase-11-polish.md, phase-12-release.md
> Read docs/design-spec/_standards.md first. This phase does not touch the wire protocol; run `/protocol-guard` anyway before committing, because it adds a manifest-shaped fixture and the guard should classify it as "no contract impact".
> Provides: a two-track demo album that ships inside the APK, is loaded on a fresh unpaired install, plays through every feature the reviewer can reach, and is replaced by the user's own library on the first real sync.

## Goal

Google Play rejected the app because a reviewer cannot pair with a Mac. The fix is to give the reviewer real content without a Mac. On first launch, when no Mac is paired and the library is empty, the app loads a small demo album from its own assets through the normal sync apply path. The reviewer then has an album, an artist, two tracks with covers, synced lyrics, ReplayGain values, and two playlists to test. The first real sync sees the demo tracks as departed and removes them, exactly as it would remove any track the Mac no longer serves.

The Play Console "App access" declaration then changes to "All functionality is available without special access", which is true: every screen works with the demo library, and the Mac is a hardware companion, not a login.

## Non-goals

- No demo podcast episode. The Podcasts tab stays empty on a demo install. A follow-up phase can add one if a reviewer asks.
- No change to the sync engine, the manifest DTOs, or `sync-protocol.md`. The demo goes through `SyncApplier.apply` like a real manifest and needs no new write path.
- No demo mode toggle, no hidden settings, no build flavour. The demo library is ordinary content that happens to come from assets.
- No scrobbling of demo plays. Demo tracks are skipped at the scrobble boundary (see plan step 7); nothing else in scrobbling changes.
- The phone still never edits files or tags. The demo files are written once by the seeder and never modified.

## Outcome shape

```
scripts/demo-media/
  generate.py                       // Mac-side generator: tones, covers, tags, lyrics, manifest.json (stdlib Python + ffmpeg + magick)
  README.md                         // how to regenerate and what must stay in step

app/src/main/assets/demo/           // mirrors the media root layout under getExternalFilesDir(null)/media
  manifest.json                     // a real Manifest document (ManifestCodec decodes it), serverId "demo"
  library/Demo/01 Demo Audio 1.mp3
  library/Demo/02 Demo Audio 2.mp3
  artwork/<sha256 of cover 1>       // no extension, same as ArtworkStore
  artwork/<sha256 of cover 2>
  lyrics/9000000001.lrc             // keyed by demo track id
  lyrics/9000000002.lrc

app/src/main/kotlin/io/cloudcauldron/bocan/app/demo/
  DemoLibrary.kt                    // eligibility, seed, clear, isActive; the only class that knows the assets exist
  DemoAssets.kt                     // tiny interface over AssetManager so tests can use a directory
  DemoPreferences.kt                // DataStore flag: auto-seeded once
app/src/main/kotlin/io/cloudcauldron/bocan/app/library/LibraryViewModel.kt   // status: content wins over not-paired
app/src/main/kotlin/io/cloudcauldron/bocan/app/library/LibraryScreen.kt      // "Try the demo library" secondary action
app/src/main/kotlin/io/cloudcauldron/bocan/app/components/EmptyState.kt      // optional secondary action
app/src/main/kotlin/io/cloudcauldron/bocan/app/settings/sections/SyncSettings.kt  // unpaired: demo status line + remove
app/src/main/kotlin/io/cloudcauldron/bocan/app/AppGraph.kt                   // wiring, scrobble skip, onHomeShown
app/src/main/kotlin/io/cloudcauldron/bocan/app/MainActivity.kt               // seed on first Home entry
app/src/main/res/values/strings.xml

app/src/test/kotlin/io/cloudcauldron/bocan/app/demo/
  DemoLibraryTests.kt               // Robolectric + in-memory Room: seed, idempotence, departure on real sync, clear
  DemoAssetsIntegrityTests.kt       // plain JVM: checked-in manifest.json matches the checked-in files byte for byte
  DirectoryDemoAssets.kt            // test DemoAssets over src/main/assets/demo

store/listing.md                    // App access flips to "no restriction"; description mentions the demo
docs/release-checklist.md           // demo library smoke steps
docs/design-spec/README.md          // index row for this phase
```

## What carries over from previous specs

- `SyncApplier.apply(manifest)` is the one write path for synced tables (phase 01). The demo uses it unchanged. `plan()` before a real apply lists departed relPaths, and `SyncEngine.deleteDeparted` removes the files (phase 03). That is how the demo leaves.
- `MediaLayout` decides where files live: tracks at `media/library/<relPath>`, artwork at `media/artwork/<hash>` (phase 03). The seeder writes only through `MediaLayout.trackFile` and `ArtworkStore.fileFor`, so validation and the FileProvider subtree for artwork stay correct.
- `SyncApplier.markDownloaded` flips rows to `Downloaded` after bytes are verified (phase 03). The seeder verifies sha256 itself and then calls it.
- `LyricsRepository` serves the cache first when the cached `lyricsHash` matches the track's (phase 06). Seeding a `LyricsCacheEntity` per track gives synced lyrics with no Mac.
- `recordApplied` updates the `sync_server` row (phase 01). That row does not exist before pairing, so the demo apply does not disturb the generation short-circuit later.
- Onboarding: every step is skippable and lands on Home (phase 11). The seeder hooks the Home entry, so both "Skip for now" and "Cancel and sync later" reach the demo.
- `store/listing.md` is the Play Console runbook (phase 12). Its App access section is rewritten here.

## Implementation plan

1. **Generate the media on the Mac** with `scripts/demo-media/generate.py` (Python 3 stdlib only; shells out to `ffmpeg`, `ffprobe`, `magick`, `shasum`). It writes everything under `app/src/main/assets/demo/` and is run by hand, never at build time. Check the outputs in. Details in "Demo media contract" below.
2. **Assets and integrity test.** Add `DemoAssetsIntegrityTests` (plain JVM, no Robolectric) that decodes `src/main/assets/demo/manifest.json` with `ManifestCodec` and asserts every size, sha256, artwork hash, and lyrics hash against the checked-in files. This test is the guard against regenerating one file and not the others.
3. **`DemoLibrary` in `:app`.** Constructor takes `DemoAssets`, `BocanDatabase`, `SyncApplier`, `MediaLayout`, `ArtworkStore`, `DemoPreferencesSource`, `CoroutineDispatchers`, and an `AppLog`. Public surface in "Definitions and contracts". Seeding order: copy tracks (to `<target>.part`, verify sha256, rename), copy artwork, then `applier.apply(manifest)`, then `markDownloaded`, then lyrics upserts. Files first, rows last, so a crash mid-way leaves stray files and no rows, and the next seed overwrites the files.
4. **Library status.** In `LibraryViewModel.status`, `total > 0` wins before `server == null`. The rest of the `when` is unchanged. Add `seeding: StateFlow<Boolean>` from `DemoLibrary` to the combine so status reports `Loading` while the seed runs, instead of flashing "No Mac paired yet".
5. **Empty state action.** `EmptyState` gains `secondaryLabel` and `onSecondary` (both optional, rendered as a `TextButton` under the primary button). `LibraryScreen` passes "Try the demo library" for `NotPaired` only, wired to `DemoLibrary.seed()`. `LibraryEmptyActions` gains `onLoadDemo`.
6. **Seed on first Home entry.** `AppGraph.onHomeShown()` launches `demoLibrary.seedOnFirstLaunch()` once per process on the app scope. `MainActivity` calls it when `entry` becomes `Home`, from both the initial resolve and the onboarding finish. `seedOnFirstLaunch` is a no-op when the DataStore flag is set, when a Mac is paired, or when any track or episode row exists.
7. **Scrobble skip.** In `AppGraph.resolveScrobbleTrack`, return null when `DemoLibrary.isDemoTrackId(trackId)`. One line, one test.
8. **Sync status when unpaired.** `SyncStatusUiState` gains `demoActive: Boolean` (from `DemoLibrary.isActive()` observed through the same flows the view model already combines: server row and track relPaths). `UnpairedContent` shows one extra line when active: "The demo album is loaded. Pairing a Mac replaces it with your own music." and a "Remove the demo album" `TextButton` that calls `AppGraph.removeDemoLibrary()`, which clears the queue (as `removeAllSyncedMedia` does) and then `DemoLibrary.clear()`.
9. **Store and release docs.** Rewrite the App access section of `store/listing.md`: select "All functionality is available without special access", delete the instruction set, and add a paragraph to the full description (below). Add a demo library block to `docs/release-checklist.md` smoke steps. Add the index row in `docs/design-spec/README.md`.
10. **Strings.** Every new user-facing string goes in `strings.xml` (en-GB spelling). The demo track titles, artist, album, and lyrics are content, not UI, and live in the assets.

## Definitions and contracts

```kotlin
/** Opens demo asset streams. AssetManager in the app; a directory in tests. */
fun interface DemoAssets {
    fun open(path: String): InputStream          // path relative to assets/demo, e.g. "library/Demo/01 Demo Audio 1.mp3"
}

interface DemoPreferencesSource {
    val autoSeeded: Flow<Boolean>
    suspend fun setAutoSeeded()
}

class DemoLibrary(...) {
    val seeding: StateFlow<Boolean>

    /** True when no Mac is paired and the tracks and episodes tables are both empty. */
    suspend fun isEligible(): Boolean

    /** True when no Mac is paired, at least one track exists, and every track relPath starts with "Demo/". */
    suspend fun isActive(): Boolean

    /** Seeds once per install: eligible and the autoSeeded flag is false. Sets the flag whether or not the seed succeeded, so a broken asset never loops. */
    suspend fun seedOnFirstLaunch()

    /** Seeds now if eligible. Idempotent: a second call while active returns AlreadyActive without writing. */
    suspend fun seed(): SeedResult

    /** Removes the demo if active: applies an empty demo manifest, deletes the Demo/ files and the two artwork files, deletes the two lyrics rows, prunes empty dirs. No-op when not active. */
    suspend fun clear()

    companion object {
        const val ID_BASE = 9_000_000_000L          // every demo id (track, artist, album, playlist) is ID_BASE + n
        const val REL_PATH_PREFIX = "Demo/"
        fun isDemoTrackId(id: Long): Boolean = id >= ID_BASE
    }
}

sealed interface SeedResult {
    data object Seeded : SeedResult
    data object AlreadyActive : SeedResult
    data object NotEligible : SeedResult
    data class Failed(val error: Throwable) : SeedResult   // MediaUnavailable, IOException, sha mismatch
}
```

Rules:

- **Ids.** Mac track ids are GRDB row ids and stay far below `ID_BASE`. Demo ids start at `9_000_000_001` so phone-local rows keyed by track id (`play_stats`, `lyrics_cache`) can never attach to a real track after a sync.
- **relPath convention.** Every demo track lives under `Demo/`. `isActive()` is derived from this and from the missing `sync_server` row, so there is no separate "demo mode" state to drift.
- **The seeder writes rows only through `SyncApplier`.** No DAO writes to synced tables from `DemoLibrary`; the only direct DAO calls are `LyricsDao.upsert` and, in `clear()`, a lyrics delete (add `LyricsDao.delete(trackId)` if absent).
- **Storage failures are typed.** `MediaLayout.trackFile` throws `SyncError.MediaUnavailable` when external storage is unmounted; `seed()` returns `Failed` and the UI shows the normal not-paired empty state. Nothing crashes.
- **Departure.** A real manifest's `plan()` must list both demo relPaths in `relPathsToDelete`, and its `apply()` must leave no row with an id at or above `ID_BASE` in tracks, albums, artists, playlists, or playlist_tracks. This is existing behaviour; the test pins it.

### Demo media contract

Two MP3 files, 60 seconds each, 44.1 kHz, stereo, 128 kbps CBR, LAME. Audible content is tones that rise and fall, so the reviewer hears something happen when they seek, and the two tracks sound clearly different.

| | Demo Audio 1 | Demo Audio 2 |
|---|---|---|
| id | 9000000001 | 9000000002 |
| relPath | `Demo/01 Demo Audio 1.mp3` | `Demo/02 Demo Audio 2.mp3` |
| tone | sine, centre 550 Hz, sweeps 220 to 880 Hz over an 8 s cycle, slow left-right pan | sine plus 2nd and 3rd harmonics, centre 330 Hz, sweeps 165 to 495 Hz over a 16 s cycle, 4 Hz tremolo |
| peak | about -12 dBFS, 0.5 s fade in and out | about -12 dBFS (the harmonics never peak together), 0.5 s fade in and out |
| trackNumber / trackTotal | 1 / 2 | 2 / 2 |
| discNumber / discTotal | 1 / 1 | 1 / 1 |
| rating (0 to 100) | 100 | 60 |
| loved | true | false |
| bpm | 120.0 | 90.0 |
| cover | warm: orange to magenta diagonal gradient, large "1", a rising wave line | cool: teal to indigo diagonal gradient, large "2", a falling wave line |

Shared fields: title as above, artist and albumArtist "Chris Funderburg", artistId and albumArtistId 9000000001, album "Bòcan Demo", albumId 9000000001, year 2026, genre "Electronic", composer "Chris Funderburg", format "mp3", sampleRate 44100, bitDepth omitted, bitrate 128, channelCount 2, isLossless false, `replayGain` with all four values, `artworkHash`, `lyricsHash`, no clip. `durationMs` comes from `ffprobe` on the encoded file, not from the requested 60 s.

Suggested `aevalsrc` expressions (the implementer may adjust; the acceptance test is by ear and by the peak level):

```
# Demo Audio 1: frequency f(t) = 550 + 330 sin(2 pi t / 8); the phase below is its integral.
aevalsrc=exprs='0.25*sin(2*PI*550*t-2640*cos(2*PI*t/8))*(0.5+0.5*sin(2*PI*t/4))|0.25*sin(2*PI*550*t-2640*cos(2*PI*t/8))*(0.5-0.5*sin(2*PI*t/4))':s=44100:d=60

# Demo Audio 2: f(t) = 330 + 165 sin(2 pi t / 16), with harmonics and tremolo.
aevalsrc=exprs='st(0,2*PI*330*t-2640*cos(2*PI*t/16));(0.2*sin(ld(0))+0.1*sin(2*ld(0))+0.05*sin(3*ld(0)))*(0.8+0.2*sin(2*PI*4*t))':s=44100:c=stereo:d=60
```

Encode with fades, ID3v2.3, and the cover attached:

```
ffmpeg -f lavfi -i "<aevalsrc>" -i cover-1.png \
  -map 0:a -map 1:v -c:a libmp3lame -b:a 128k -c:v copy -disposition:v attached_pic \
  -af "afade=t=in:d=0.5,afade=t=out:st=59.5:d=0.5" \
  -id3v2_version 3 -write_id3v1 1 \
  -metadata title="Demo Audio 1" -metadata artist="Chris Funderburg" -metadata album_artist="Chris Funderburg" \
  -metadata album="Bòcan Demo" -metadata track="1/2" -metadata disc="1/1" -metadata date="2026" \
  -metadata genre="Electronic" -metadata composer="Chris Funderburg" -metadata TBPM="120" \
  -metadata comment="Demo track bundled with Bòcan Music for Android. Replaced by your own library on the first sync." \
  -metadata copyright="2026 Chris Funderburg. CC0 1.0." -metadata publisher="Cloud Cauldron" -metadata TLAN="eng" \
  -metadata lyrics="<full LRC text>" \
  -metadata REPLAYGAIN_TRACK_GAIN="<from ffmpeg replaygain filter> dB" -metadata REPLAYGAIN_TRACK_PEAK="<peak>" \
  -metadata REPLAYGAIN_ALBUM_GAIN="<mean of the two track gains> dB" -metadata REPLAYGAIN_ALBUM_PEAK="<max of the two peaks>" \
  -metadata:s:v title="Album cover" -metadata:s:v comment="Cover (front)" \
  "01 Demo Audio 1.mp3"
```

Verify the tags landed with `ffprobe -show_format -show_streams`. ffmpeg maps `lyrics` to USLT and unknown upper-case keys to TXXX; if any frame is missing, the generator may fall back to `kid3-cli` or `eyeD3` and the README must say so. ReplayGain values come from `ffmpeg -i <file> -af replaygain -f null -` (parse `track_gain` and `track_peak` from stderr). The phone reads metadata from the manifest, not from the tags, but the tags must agree with the manifest so a user who pulls the file over USB sees the same thing.

Covers: 1000 by 1000 PNG via ImageMagick, flat gradients and simple shapes so each file stays under about 150 KB. No text other than the digit. The two covers must be distinguishable at 48 dp (the mini player and the widget), so use opposite colour temperatures and opposite wave directions, not only a different digit. Store each at `assets/demo/artwork/<sha256 of the PNG bytes>` with no extension.

### Synced lyrics

Both tracks carry synced lyrics, even though the audio has no words. The point is to exercise the lyrics view: the highlighted line must advance in time with the tone, and seeking must jump the highlight. Each track has its own LRC file with different text, so the reviewer can see the lyrics change when the track changes.

The lyrics reach the phone in two places:

1. **The lyrics cache.** The seeder upserts one `LyricsCacheEntity` per track with `kind = LyricsKind.Synced`, `lyricsHash` equal to the sha256 of the LRC file bytes, and `text` equal to the file contents. This is what the Now Playing lyrics view reads, through `LyricsRepository`, with no Mac.
2. **The MP3 tag.** The generator writes the same LRC text into the file's USLT frame with `-metadata lyrics=`. The phone never reads it, but the file then matches what the phone shows, and a desktop player that reads LRC-in-USLT shows timed lines too. ffmpeg cannot write a SYLT frame; do not try to add one.

Rules for the LRC text: timestamps every 4 to 6 seconds from 0.5 s to about 55 s so no long gap looks like a stall; the `[ti:]`, `[ar:]`, `[al:]` header tags; plain English; honest about being a demo; no em or en dashes. The two files must differ in every timed line.

Track 1 (`lyrics/9000000001.lrc`):

```
[ti:Demo Audio 1]
[ar:Chris Funderburg]
[al:Bòcan Demo]
[00:00.50]This is the Bòcan demo album.
[00:05.00]The tone you hear rises and falls.
[00:10.00]These lyrics are synced to the music.
[00:15.00]Each line lights up on time.
[00:20.00]Drag the seek bar and watch it jump.
[00:25.00]Swipe left for the second demo track.
[00:30.00]Try the queue, the equalizer, and gestures.
[00:35.00]Rate a song, or mark it loved, on your Mac.
[00:40.00]Pair a Mac to sync your own library.
[00:45.00]This demo is replaced on your first sync.
[00:50.00]Nothing here leaves your phone.
[00:55.00]Thanks for listening.
```

Track 2 (`lyrics/9000000002.lrc`):

```
[ti:Demo Audio 2]
[ar:Chris Funderburg]
[al:Bòcan Demo]
[00:00.50]Second track, second cover, second set of lyrics.
[00:05.00]This tone is lower and moves more slowly.
[00:10.00]The tremolo you hear is four beats a second.
[00:15.00]Lyrics come from the Mac, one file per song.
[00:20.00]Timing offset lives under the lyrics toggle.
[00:25.00]Open song details for the format and bitrate.
[00:30.00]Add this song to the queue and shuffle it.
[00:35.00]The playlist tab has a manual and a smart list.
[00:40.00]Only the loved track is in the smart list.
[00:45.00]Your first sync removes both demo songs.
[00:50.00]The demo album is not scrobbled.
[00:55.00]That is the end of the demo.
```

`lyricsHash` is the sha256 of the LRC file bytes. The generator computes it and writes it into `manifest.json`; the integrity test checks it.

Playlists in the manifest:

| id | name | kind | accentColor | artworkHash | trackIds |
|---|---|---|---|---|---|
| 9000000001 | Demo Playlist | manual | `#FF7A00` | cover 1 | [9000000001, 9000000002] |
| 9000000002 | Loved Demo Tracks | smart | `#1FA3A3` | cover 2 | [9000000001] |

Check `manifest-small.json` for the exact `accentColor` format the Mac emits and match it.

Manifest envelope: `protocolVersion` 1, `serverId` "demo", `serverName` "Demo library", `generation` 0, `generatedAt` the generation timestamp, no podcasts, no episodes.

### Play Console copy

App access: select "All functionality is available without special access". Remove the instruction set. Keep the demo video links in the foreground-service declarations, which have their own fields.

Add to the full description, after the first paragraph:

```
No Mac nearby? A short demo album is built in, so you can try the player, lyrics, equalizer, and playlists right away. Your first sync replaces it with your own music.
```

## Context7 lookups

- use context7: AssetManager open asset InputStream Android
- use context7: DataStore preferences booleanPreferencesKey
- use context7: Robolectric getExternalFilesDir temp directory in unit tests
- use context7: ffmpeg aevalsrc expression syntax and afade
- use context7: ffmpeg id3v2 metadata mapping USLT TXXX attached_pic

## Dependencies

No new Gradle dependencies. Mac-side tooling for the generator only: ffmpeg (with libmp3lame), ImageMagick 7 (`magick`), Python 3, `shasum`. All are present on the development Mac; `rsgain` is not, so album gain is derived as described.

## Test plan

- `DemoAssetsIntegrityTests` (JVM): decode `manifest.json`; for every track assert the file exists at `library/<relPath>`, `size` equals the file length, `sha256` equals the file hash, `id >= ID_BASE`, `relPath` starts with `Demo/`, `format == "mp3"`; for every `artworkHash` the artwork file exists and hashes to its name; for every `lyricsHash` the LRC file hashes to it; playlists reference only demo track ids; no podcasts or episodes.
- `DemoLibraryTests` (Robolectric, in-memory Room on the bundled driver, `MediaLayout` over Robolectric's external files dir, `DirectoryDemoAssets` over `src/main/assets/demo`):
  1. `isEligible` is true on a fresh DB, false when a `sync_server` row exists, false when any track exists.
  2. `seed()` writes 2 tracks (both `Downloaded`), 1 album, 1 artist, 2 playlists with the right membership, 2 lyrics rows, 2 track files at `mediaLayout.trackFile(relPath)` with matching sha256, and 2 artwork files at `artworkStore.fileFor(hash)`.
  2a. After `seed()`, `LyricsRepository.lyricsFor(id, lyricsHash)` with a fetcher that always returns `Unreachable` yields `Loaded` with `kind == Synced` and at least 12 timed lines for each track, and the two documents differ.
  3. A second `seed()` returns `AlreadyActive` and changes nothing (compare row snapshots and file mtimes).
  4. `seedOnFirstLaunch()` seeds once; a second call with an empty DB and the flag set does nothing.
  5. After seeding, `applier.plan(fixtureManifest())` lists exactly the two demo relPaths to delete; after `apply`, no row in tracks, albums, artists, playlists, or playlist_tracks has an id at or above `ID_BASE`; `isActive()` is false.
  6. `clear()` removes the rows, the two track files, the two artwork files, the `Demo/` directory, and the lyrics rows, and `isEligible()` is true again.
  7. A `DemoAssets` that throws `IOException` on the second track leaves no rows and returns `Failed`.
  8. A sha256 mismatch (assets that return altered bytes) returns `Failed` and writes no rows.
- `LibraryViewModelTests`: add "status is content when unpaired but tracks exist" and "status is loading while the demo is seeding".
- `AppGraph` scrobble mapping: a track with id `ID_BASE + 1` resolves to null; a track with id 1 resolves as before.
- Compose semantics (`ComponentUiTests`): `EmptyState` renders the secondary action when both parameters are given and omits it otherwise.
- Manual, on a device with a fresh install: skip onboarding, see the demo album at once, play both tracks, swipe between them and watch the cover change, open lyrics, open song details, rate and love display, shuffle the playlist, EQ, lock screen and notification artwork, widget, Android Auto DHU browse. Then pair and sync a real Mac and confirm the demo is gone from every tab and from `media/library/Demo/`.

## Acceptance criteria

- [ ] A fresh unpaired install shows the demo album on the Library screen without any tap beyond skipping onboarding.
      Device check, listed in `docs/release-checklist.md`. The path is wired (`MainActivity` calls `onHomeShown` on both roads to Home) and `DemoLibraryTests` pins `seedOnFirstLaunch`.
- [ ] Both tracks play end to end, show their own cover, synced lyrics, ReplayGain values, rating, and loved state.
      Device check. The rows carry every field (`DemoAssetsIntegrityTests`) and the files land where the player reads them (`DemoLibraryTests`).
- [ ] The lyrics view highlights each line at its timestamp, follows a seek, and shows different text for each track.
      Device check for the highlight; `DemoLibraryTests` proves `LyricsRepository` serves two different synced documents of 12 lines with no Mac.
- [ ] Both playlists open and play; the smart one shows only the loved track.
      Device check for playback; membership (manual: both, smart: the loved track only) is pinned by `DemoLibraryTests`.
- [x] The first real sync removes every demo row and file, with no leftover folder under `media/library/`. (`DemoLibraryTests`: the real manifest's plan lists both demo relPaths, apply leaves no demo row; file deletion and pruning are the engine's existing departure path, and `clear()` exercises the same layout calls.)
- [x] "Try the demo library" on the not-paired empty state reseeds after "Remove the demo album". (Wired through `LibraryEmptyActions.onLoadDemo` and `SyncSettingsCallbacks.onRemoveDemo`; `DemoLibraryTests` covers clear then seed.)
- [x] Demo plays never reach a scrobble provider. (`AppGraph.resolveScrobbleTrack` returns null for `DemoLibrary.isDemoTrackId`; the id rule is tested.)
- [x] `DemoAssetsIntegrityTests` fails if any asset is changed without regenerating the manifest.
- [x] `store/listing.md` App access section says "no restriction" and the full description mentions the demo.
- [x] `./gradlew check test koverVerify` green; `/android-standards` and `/protocol-guard` run and clean.

## Gotchas

- **Do not put demo rows through the DAOs directly.** The whole point is that the demo is indistinguishable from a synced library, so departure, pruning, artwork lookup, and Auto browsing all work for free. If a DAO write is ever needed, the design is wrong.
- **`LibraryStatus` order matters.** Today `server == null` is checked first, so an unpaired phone with content shows "No Mac paired yet". Content must win, or the demo is invisible.
- **Ids must stay above `ID_BASE`.** Phone-local tables are keyed by track id and survive syncs by design. A demo track with id 1 would donate its play count to the Mac's track 1.
- **The `sync_server` row must not exist during seeding.** `recordApplied` is a no-op without it, which is what keeps `lastAppliedGeneration` untouched for the real Mac. The eligibility check guarantees this; do not relax it.
- **Assets are not files.** `AssetManager` streams are read-only and have no path, so copy to `<target>.part` and rename, and verify sha256 while copying. Never hand an asset Uri to ExoPlayer; the player must read the same `file://` path it reads for synced tracks.
- **Artwork files have no extension.** `ArtworkStore.fileFor(hash)` is the only correct target; the FileProvider path in `file_paths.xml` already covers `media/artwork/`.
- **Regenerating media changes hashes.** The generator rewrites `manifest.json` and renames the artwork files; commit them together. The integrity test catches a partial update, but only if it runs, so keep it in the default `test` task.
- **`sync_remove_media` stays paired-only.** The unpaired screen gets its own "Remove the demo album" action instead, because remove-all leaves rows pending and there is no Mac to refill them.
- **Play review may still ask about the Mac.** Keep the pairing and sync demo videos current in `store/`; they remain attached to the foreground-service declarations.
- **No em dashes or en dashes** in the LRC, the tags, the manifest, or the copy. The comment tag and the lyrics are user-visible text.

## Handoff

The demo library is content, not a mode. Later phases that add browse surfaces (a demo podcast, Auto shelves) only need to extend `manifest.json` and the generator; the seeder and departure path need no change. If the Mac side ever serves a `Demo/` folder of its own, raise `REL_PATH_PREFIX` to something the Mac sanitiser cannot produce before merging.

Open decision for Chris before the media is generated: the licence text in the copyright tag defaults to CC0 1.0. Change it in the generator if you want to keep the rights.
