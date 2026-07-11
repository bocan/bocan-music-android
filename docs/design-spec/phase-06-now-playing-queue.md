# Phase 06: Now Playing, Queue, Lyrics, Sleep Timer

> Depends on: phase-04-playback-engine.md, phase-05-library-ui.md
> Read docs/design-spec/_standards.md first.
> Provides: the full-screen Now Playing experience, the queue sheet, synced lyrics, and the sleep timer.

## Goal

The screen you live in: big artwork, transport, seek, shuffle/repeat, loved and rating display, a draggable queue sheet, karaoke-style synced lyrics fetched from the Mac, and a sleep timer that fades out instead of cutting.

## Non-goals

- No editing: loved/rating are display-only (they come from the Mac).
- No podcast transport variant (phase 07 adds skip intervals and speed UI).
- No EQ surface (phase 08).

## Outcome shape

```
app/src/main/kotlin/io/cloudcauldron/bocan/app/player/
  NowPlayingScreen.kt  NowPlayingViewModel.kt
  TransportControls.kt SeekBar.kt
  QueueSheet.kt        QueueViewModel.kt
  LyricsPane.kt        LyricsViewModel.kt
  SleepTimerSheet.kt
core/playback/src/main/kotlin/io/cloudcauldron/bocan/playback/
  lyrics/{LrcParser.kt, LyricsRepository.kt}
  SleepTimer.kt
```

## Definitions and contracts

- `LrcParser` (pure): parses LRC to `List<LyricLine(timeMs, text)>`. Handles multiple timestamps per line (`[01:02.00][02:15.50]chorus`), centisecond and millisecond precision, metadata tags (`[ar:]`, `[ti:]`, `[offset:+500]` applied to all lines), blank lines, and garbage lines (skipped, never thrown). Unsynced input (no timestamps at all) yields a single-mode result: `LyricsDoc.Unsynced(text)` vs `LyricsDoc.Synced(lines)`.
- `LyricsRepository`: for the current track, consult `lyrics_cache` by `lyricsHash`; on miss and when the paired Mac is reachable, `GET /v1/lyrics/{trackId}` via the paired client, cache, emit. Offline with no cache emits `None`. Never blocks playback.
- `SleepTimer` (in `:core:playback`): `start(duration)`, `extend(minutes)`, `cancel`, `state: StateFlow<SleepTimerState>`. On expiry: ramp player volume from current to 0 over 10 s, pause, restore volume. "End of current track" mode waits for the item transition then fades fast (2 s).

## Implementation plan

1. `NowPlayingScreen`: full-screen route from the mini player. Layout: large artwork (with a subtle ambient background derived from artwork palette), title/artist/album (tap artist/album navigates), loved heart + rating stars (display-only), seek bar with elapsed/remaining, transport (previous, play/pause, next), shuffle and repeat toggles, overflow menu (Sleep timer, Speed, Lyrics toggle, Go to queue).
2. `SeekBar`: position ticker from `PlayerUiState` (1 s cadence), drag preview with time bubble, seek on release only.
3. `QueueSheet`: bottom sheet listing the queue with the current item highlighted; drag-to-reorder and swipe-to-remove (queue edits are session-local and allowed; the library is what the phone must not edit); "Up Next" header shows count and total remaining time; Clear queue action with confirmation.
4. Shuffle/repeat controls map to `QueueController`; a long-press on shuffle picks the strategy (Shuffle, Smart Shuffle) with a hint line explaining smart shuffle.
5. Lyrics: a swipeable page or toggle within Now Playing. Synced mode: centered column, active line emphasized, auto-scroll with spring animation, tap a line to seek. Unsynced: scrollable text. Respect reduced motion (jump instead of animate). Offset chip if the doc came with `[offset:]`.
6. Speed control (music context): 0.5x to 2.0x stepper in the overflow, resets to 1.0x on demand; persisted per session only for music (per-show persistence is phase 07's).
7. `SleepTimerSheet`: presets (15/30/45/60 min, End of track), countdown display in the sheet and a small moon indicator on Now Playing while armed, extend button.
8. Palette extraction for the ambient background via androidx.palette from the artwork bitmap, cached per artworkHash, disabled under battery saver.

## Context7 lookups

- use context7: Compose ModalBottomSheet drag reorder lazy list patterns
- use context7: androidx palette usage with Coil bitmaps

## Dependencies

androidx.palette. Test: Turbine, Robolectric where needed.

## Test plan

### LrcParser (property-heavy, mirrors the Mac's parser tests)
- Multi-timestamp lines expand to multiple entries, sorted.
- `[offset:+500]` and negative offsets shift all lines; clamping at 0.
- cs vs ms precision both parse to correct ms.
- Garbage interleaved with valid lines: valid lines survive, count exact.
- Wholly unsynced text becomes `Unsynced`.
- Round-trip stability: parse(sample fixtures) matches golden JSON.

### LyricsRepository
- Cache hit by hash serves without network; hash change refetches (MockWebServer); offline+miss -> `None`; server 404 -> `None` cached negatively for the session.

### SleepTimer
- Virtual-time tests: expiry fades then pauses and restores volume; extend during countdown; end-of-track mode triggers on transition; cancel restores immediately.

### Queue UI
- Reorder emits the right `move(from, to)`; remove of the playing item advances playback.

## Acceptance criteria

- [ ] Now Playing shows live state, seeks smoothly, and navigates to artist/album.
  - The screen, view model state mapping, seek-on-release, and artist/album navigation are implemented, but live state and smooth seeking need playback on a device.
- [x] Queue sheet reorders and removes; changes are reflected in actual playback order.
- [ ] Synced lyrics auto-scroll in time and tap-to-seek works; unsynced lyrics scroll; absent lyrics show a quiet empty state.
  - The parser, repository, and active-line-from-position logic are unit-tested and the pane is built (auto-scroll, tap-to-seek, unsynced, empty state), but the visual auto-scroll needs a device.
- [x] Sleep timer fades out over 10 s and pauses; end-of-track mode works; timer state survives navigating away.
- [ ] Reduced motion disables auto-scroll animation and ambient background transitions.
  - The reduced-motion branch is implemented in the lyrics scroll and ambient background, but the visual result needs a device.
- [x] Loved and rating render but expose no edit affordance.
- [x] All strings localized; TalkBack announces transport state changes.

## Gotchas

- **Lyrics sync uses player position, not wall clock.** Poll `PlayerUiState` position; do not run an independent timer that drifts.
- **Fade must restore volume.** The fade lowers `player.volume`; pausing without restoring leaves the next play silent. Restore in a `finally`.
- **Queue reorder vs shuffle**: reordering while shuffled edits the shuffled order (what the user sees is what plays). Do not secretly maintain a hidden unshuffled order the user cannot see; keep the model observable and simple.
- **Palette on the main thread will jank**; extract on a background dispatcher, cache aggressively.

## Handoff

Phase 07 assumes Now Playing can host an alternate transport row per item type and the speed control seam exists. Phase 08 assumes the overflow menu can gain an Equalizer entry.
