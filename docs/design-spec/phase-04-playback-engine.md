# Phase 04: Playback Engine

> Depends on: phase-01-persistence.md, phase-03-sync-engine.md (files on disk)
> Read docs/design-spec/_standards.md first.
> Provides: `:core:playback`: the Media3 player, `MediaLibraryService`, gapless queue, ReplayGain, speed, resume, and the session that every UI surface (app, Auto, widget, Bluetooth) talks to.

## Goal

Press play on any synced track and it sounds right: gapless album playback, ReplayGain-corrected loudness, correct metadata on the lock screen and Bluetooth, positions that survive process death, and a queue model faithful to the Mac app (Up Next, repeat, shuffle including smart shuffle, exclude-from-shuffle).

## Non-goals

- No EQ or effects chain yet (phase 08 attaches to the seams built here).
- No UI beyond what the session gives the system for free (phase 05/06 build the screens).
- No podcast-specific behaviours (phase 07 layers resume/speed rules on this engine).

## Outcome shape

```
core/playback/src/main/kotlin/io/cloudcauldron/bocan/playback/
  PlaybackService.kt            // MediaLibraryService, session, notification
  PlayerFactory.kt              // ExoPlayer construction: renderers, audio attrs, seams
  queue/QueueController.kt      // the app-facing transport + queue API
  queue/ShuffleStrategy.kt      // FisherYates + SmartShuffle
  queue/QueuePersistence.kt     // survive process death
  audio/ReplayGainProcessor.kt  // AudioProcessor applying gain
  audio/ReplayGainMode.kt       // Off / Track / Album
  stats/PlayStatsRecorder.kt    // playCount / skip rules -> PlayStatsDao
  MediaItemFactory.kt           // TrackEntity/EpisodeEntity -> MediaItem
  PlaybackError.kt
```

## Definitions and contracts

- `MediaItemFactory`: builds `MediaItem`s with `mediaId = "track:<id>"` or `"episode:<id>"`, local `Uri` from `MediaLayout`, full `MediaMetadata` (title, artist, album, artwork Uri via `ArtworkStore`, track/disc numbers, duration). Clip tracks get `ClippingConfiguration(startMs, endMs)` pointing at the source track's file. ReplayGain values ride in `MediaItem.localConfiguration.tag` as a small data class.
- `ReplayGainProcessor`: a Media3 `AudioProcessor` inserted via `DefaultAudioSink` in a custom `RenderersFactory`. Applies a linear gain computed as `10^((gain + preamp) / 20)`, clamped so `peak * factor <= 1.0` (use the peak value to prevent clipping). Gain updates on media item transitions via a listener that reads the tag. Mode Off bypasses. Preamp default 0 dB, adjustable later.
- `QueueController` public API (suspend/main-safe, backed by the session's player on the main thread):
  `playNow(ids, startIndex)`, `playNext(ids)`, `addToQueue(ids)`, `removeAt(index)`, `move(from, to)`, `skipNext/skipPrevious`, `seekTo(ms)`, `setRepeat(mode)`, `setShuffle(strategy?)`, `setSpeed(rate)`, plus `state: StateFlow<PlayerUiState>` (current item, position ticker, queue snapshot, repeat/shuffle flags).
- `ShuffleStrategy`:
  - `FisherYates`: uniform permutation of the remaining items.
  - `SmartShuffle`: weight each track by `1 / (1 + skipCount)` and recency damping (recently played in this session sinks); implement as weighted sampling without replacement. Tracks with `excludedFromShuffle` semantics: the Mac schema has the flag but it is not in the manifest v1; honour a `PlayStatsDao`-adjacent local exclusion set instead, and note the manifest gap in the PR (candidate additive field).
- `PlayStatsRecorder` (mirrors the Mac rules): a play counts when 50 percent or 4 minutes is reached (track must be at least 30 s); a skip records `skipAfterSeconds` when the user advances before 50 percent. Positions write `playDurationTotalSec` incrementally. Emits scrobble-eligible events on a `SharedFlow` for phase 09 (podcasts flagged so scrobbling skips them).
- `QueuePersistence`: serialize queue media ids, index, position, repeat/shuffle every 5 s while playing and on every transition, restore on service start. Never auto-resume playback on restore; restore paused.

## Implementation plan

1. Dependencies: `media3-exoplayer`, `media3-session`, `media3-common`, plus `media3-decoder-ffmpeg`. The FFmpeg extension is not published on Maven by Google; decide between (a) the Jellyfin-published prebuilt (`org.jellyfin.media3:media3-ffmpeg-decoder`, tracks upstream Media3 versions) and (b) building the extension from the androidx/media source with an NDK toolchain. Default to (a) for v1 with the decision recorded in the PR; pin its version to match the Media3 version exactly.
2. `PlayerFactory`: `DefaultRenderersFactory` with `EXTENSION_RENDERER_MODE_PREFER` (FFmpeg audio renderer used when the platform decoder cannot), audio attributes `USAGE_MEDIA / CONTENT_TYPE_MUSIC`, `setHandleAudioBecomingNoisy(true)`, audio focus handled by the player, wake mode local. Insert `ReplayGainProcessor`.
3. `PlaybackService : MediaLibraryService`: one `MediaLibrarySession`; notification via the default `MediaNotification.Provider` (customisation in phase 10); `onGetLibraryRoot` returns a stub tree until phase 10 fills it for Auto. Task-removal behaviour: keep playing.
4. `MediaItemFactory` + clip handling + artwork Uris.
5. `QueueController` bound to a `MediaController` connected to the service; queue mutations map to player timeline ops. Shuffle is implemented by reordering the actual queue (deterministic, testable), not ExoPlayer's built-in shuffle order.
6. `PlayStatsRecorder` listening to the player: transitions, position polling (1 s while playing), completion.
7. `QueuePersistence` (DataStore proto or JSON in a file) + restore-on-start.
8. Resume-position rule for long tracks: any item over 20 minutes (audiobook-ish or a clip source that long) restores its last position; regular music restarts. (Podcast rules replace this for episodes in phase 07.)
9. Wire into `AppGraph`; a temporary debug screen button ("play first album") proves audio end to end until phase 05 lands.

## Context7 lookups

- use context7: Media3 1.10 MediaLibraryService MediaLibrarySession setup and notification provider
- use context7: Media3 custom AudioProcessor in DefaultAudioSink via RenderersFactory
- use context7: Media3 ClippingConfiguration gapless playback behaviour
- use context7: Media3 FFmpeg decoder extension EXTENSION_RENDERER_MODE_PREFER configuration

## Dependencies

Media3 (exoplayer, session, common), FFmpeg decoder artifact per step 1, DataStore. Test: Robolectric for service-less player tests where possible; keep pure logic (shuffle, stats rules, factories) JVM-pure.

## Test plan

### Pure logic (JVM)
- `ShuffleStrategy`: permutation validity (no dupes/losses), FisherYates uniformity smoke (chi-squared loose bound over 10k runs, seeded), SmartShuffle sinks high-skip tracks (statistical assertion, seeded RNG).
- `PlayStatsRecorder` rules table: 29 s track fully played -> ineligible; 10 min track played 4 min -> play; skip at 40 percent -> skip with `skipAfterSeconds`; pause/resume does not double-count.
- `MediaItemFactory`: metadata mapping, clip config, replaygain tag, episode vs track mediaId round-trip parsing.
- `ReplayGainProcessor` math: gain factor from dB, peak clamp, off-mode bypass (feed PCM buffers, assert samples scaled).

### Robolectric
- `QueuePersistence` round-trip including process-death simulation (new controller restores paused at position).
- Service starts, session connects, `playNow` reaches STATE_READY with a silent test asset.

## Acceptance criteria

- [ ] A FLAC album plays gapless (manual listening check on device with a known gapless album, plus automated: consecutive items report no `DISCONTINUITY_REASON_AUTO_TRANSITION` gap anomalies).
- [ ] An APE or WavPack file (unplayable by the platform) plays via the FFmpeg renderer.
- [ ] ReplayGain audibly and measurably levels two tracks with different gains; peak clamp prevents clipping.
- [ ] Lock screen and Bluetooth show correct title/artist/artwork; audio pauses on unplug (becoming-noisy).
- [ ] Speed 0.5x to 2.0x with pitch preserved.
- [ ] Queue survives process death, restores paused.
- [ ] Play/skip stats recorded per the rules table.
- [ ] CUE clip tracks play their windows gapless-adjacent.
- [ ] Kover floor holds for `:core:playback` (pure-logic classes carry the coverage; service glue exempt via annotation-scoped exclusion, documented).

## Gotchas

- **ExoPlayer gapless relies on encoder delay/padding metadata** (MP3 LAME tags, AAC). FLAC/WAV are inherently gapless. Do not add a crossfade hack here to mask a gapless bug; fix the item configuration.
- **The FFmpeg artifact's version must match Media3's exactly**; a minor mismatch produces runtime `LinkageError`s, not compile errors.
- **AudioProcessor runs on the audio thread.** No allocation per buffer, no locks; read the gain from a `@Volatile` field updated on transitions.
- **MediaController calls must happen on the main thread**; `QueueController` is the choke point that guarantees it.
- **Clip tracks share a file**: two clips of the same source must not double-download artwork or confuse `mediaId` parsing; ids are `track:<clipTrackId>`, never the source id.
- **Do not persist the queue on every position tick**; 5 s cadence plus transitions, matching the Mac's write-back cadence.

## Handoff

Phase 05/06 assume `QueueController` and `PlayerUiState`. Phase 07 assumes `MediaItemFactory` handles episodes and `PlayStatsRecorder` flags podcasts. Phase 08 assumes `PlayerFactory` exposes the audio session id and the processor insertion seam. Phase 09 assumes the scrobble-eligible event flow. Phase 10 assumes `MediaLibraryService` is the single session owner.
