# Phase 13: Now Playing Gestures and Song Details

> Depends on: phases 04, 06, 07, 11 (it layers gestures onto the finished Now Playing screen)
> Slots before phase 12 in execution order: release assumes this phase is complete.
> Read docs/design-spec/_standards.md first.
> Provides: touch-first navigation on Now Playing (swipe to change track, reveal details, dismiss) and the song details sheet.

## Goal

Make Now Playing feel like a physical object under the finger: horizontal swipes change tracks, an upward swipe reveals a new song details sheet, a downward swipe dismisses the player back to whatever is beneath it (the library with the mini player docked). Every gesture is finger-driven with a gradual, interruptible transition, degrades to a snap under reduced motion, and has a button or menu path so no capability is gesture-only.

## Non-goals

- No gestures anywhere but Now Playing (library swipe actions are a separate idea for the backlog).
- No editing from the details sheet: it is read-only, the Mac owns metadata.
- No protocol changes. The one new session command is phone-internal (app process to playback service); sync-protocol.md is untouched.

## Outcome shape

```
app/src/main/kotlin/io/cloudcauldron/bocan/app/player/
  PlayerGestures.kt            // the gesture state machines + Modifier.playerGestures(...)
  SongDetailsSheet.kt          // read-only metadata sheet (track and episode variants)
  SongDetailsViewModel.kt      // folds db row + play stats + live audio format
core/playback/src/main/kotlin/io/cloudcauldron/bocan/playback/session/
  SessionCommands.kt           // + GET_AUDIO_FORMAT custom command (service answers from ExoPlayer)
```

## Definitions and contracts

Gesture surface: the `ArtworkAndMeta` block only. The top row, seek bar, transport, lyrics pane, and every sheet keep their own input untouched. In lyrics mode the surface is not shown, so player gestures are absent there (v1).

Directions are physical, verbatim, and do not mirror under RTL (transport is muscle memory):

| Gesture | Action | Non-gesture path |
|---------|--------|-----------------|
| Swipe right | Next track (same call as the transport's next) | Transport next button |
| Swipe left | Previous track (same call as the transport's previous) | Transport previous button |
| Swipe up | Open the song details sheet | New overflow item "Song details" |
| Swipe down | Dismiss Now Playing (popBackStack) | Existing collapse button, system back |

Motion contract: the block follows the finger (horizontal offset for track changes, whole-screen vertical offset for dismiss). During a horizontal drag the neighboring item's card, its real artwork and titles, tracks in from the opposite edge in lockstep with the finger: the two cards move as one strip, so the user sees exactly where they are going before they commit. Commit at 40 percent of the travel distance or a fling; the strip completes the slide and the neighbor becomes current. Cancel springs the strip back and the neighbor retreats off-edge. No placeholders: the peek renders the neighbor's actual artwork, prefetched so the first drag frame already has it. Under reduced motion (Motion.kt): no slides and no peek, content swaps and the dismiss pops without translation. At the queue's ends there is no neighbor, so the drag rubber-bands (fractional resistance) and always settles back with no action.

Accessibility: the gesture surface carries four `CustomAccessibilityAction`s (next track, previous track, song details, close player), and the merged content description it already has stays intact.

Song details sheet (ModalBottomSheet), track variant, one labelled row per known value, unknown rows omitted: title, artist, album, album artist, year, genre, track and disc number, format with a lossless badge, duration, file size (Formatter.formatShortFileSize), derived average bitrate (size x 8 / durationMs, only when both known), play count and last played (play stats), loved and rating, and the live audio pipeline line (sample rate, bit depth or encoding, channel count) fetched via GET_AUDIO_FORMAT when the sheet's item is the playing item. Episode variant: title, show, published date, duration, file size, format, play position. Every label is a string resource; numbers go through platform formatters.

GET_AUDIO_FORMAT: a Media3 custom session command in `SessionCommands` (the SKIP_BACK pattern). The service answers from `ExoPlayer.getAudioFormat()` with a Bundle (sampleRateHz: Int, channelCount: Int, encoding: String, bitDepth: Int when derivable, else absent). `QueueController` grows one main-safe suspend accessor the view model calls; absent or failed answers render no pipeline line, never an error.

## Implementation plan

1. **Session command first** (test-first in :core:playback): add GET_AUDIO_FORMAT, handle it in the session callback, cover the round trip with the existing Robolectric session harness.
2. **SongDetailsViewModel**: resolve `state.current.mediaId` via `MediaId.parse` to a track or episode row plus play stats; expose one immutable UiState; fetch the pipeline line lazily when shown.
3. **SongDetailsSheet**: both variants, merged semantics per row, headings marked, all strings resourced.
4. **Overflow entry**: add "Song details" to the Now Playing overflow so the sheet is discoverable without the gesture.
5. **Neighbor prefetch**: `NowPlayingViewModel` grows previous and next neighbor display state (title, artist, artwork uri), resolved from the queue's adjacent mediaIds through the same db lookups the current item uses, refreshed on every transition, and with the artwork warmed through Coil at resolve time so the first drag frame renders the real image. A null neighbor is the end-of-queue signal the gesture machine rubber-bands on.
6. **PlayerGestures.kt**: two `anchoredDraggable` state machines (horizontal three-anchor: previous / settled / next; vertical three-anchor: details / settled / dismiss) with axis lock on initial drag direction, threshold and fling commit, rubber-banding when the neighbor is null, reduced-motion snap, and the four custom accessibility actions. Pure decision logic (anchor resolution from offset + velocity, end-of-queue clamping) lives in plain functions for JVM tests.
7. **Wire into NowPlayingScreen**: apply the modifier to `ArtworkAndMeta`, render the two-card strip from gesture progress plus the neighbor state, call the same view model functions as the transport buttons, popBackStack on committed dismiss.
8. Run the phase 11 guards over the new UI (they are in CI already); update the accessibility audit doc's fixed list with the new custom actions.

## Context7 lookups

- use context7: Compose Foundation anchoredDraggable anchors, thresholds, and fling behavior
- use context7: Media3 custom session commands returning results from MediaSession.Callback
- use context7: Material 3 ModalBottomSheet with dynamic content and accessibility

## Dependencies

None new. Compose Foundation (anchoredDraggable) and Media3 session are already in the catalog.

## Test plan

- Anchor decision functions: offset and velocity to target anchor, both axes, all thresholds; a null neighbor clamps to settled.
- Neighbor resolution: adjacent mediaIds resolve to the right display rows for tracks and episodes; ends of the queue yield null; a queue mutation refreshes both neighbors.
- Session command: GET_AUDIO_FORMAT round trip returns the playing format; absent format returns an empty result.
- SongDetailsViewModel: track and episode resolution, bitrate derivation (absent when size or duration unknown), stats folding.
- Semantics: the gesture surface exposes exactly the four custom actions; the details sheet rows merge and label correctly.
- Manual on device: feel of thresholds, rubber-banding, reduced-motion snap, TalkBack action menu.

## Acceptance criteria

- [x] Swipe right advances to the next song, swipe left to the previous, matching the transport buttons exactly, with the block tracking the finger and settling with a spring (snap under reduced motion). (Gesture calls the same `NowPlayingViewModel.next`/`previous` as the transport; `resolveHorizontalTarget` unit tested; on-device feel of the spring is a manual check.)
- [x] The neighboring song's real artwork and titles peek in with the drag, in lockstep with the finger, from the first frame of the gesture; no placeholder art ever shows during a drag. (Neighbor state resolved from the queue's actual order and warmed through Coil on every transition, both unit tested; the first-frame render is a manual on-device check.)
- [x] Swipe up opens the song details sheet; the same sheet opens from the overflow menu.
- [x] Swipe down dismisses Now Playing to the screen beneath, finger-driven, and cancels cleanly on release before threshold. (`resolveVerticalTarget` unit tested; the commit animates the screen down then popBackStack.)
- [x] The details sheet shows every known field for a track (including derived bitrate and the live pipeline line while playing) and the episode variant for podcasts; unknown fields are omitted, nothing shows a raw null or 0.
- [x] At either end of the queue the horizontal drag rubber-bands and does nothing.
- [x] TalkBack can invoke all four gestures as custom actions, and none of the four capabilities is reachable only by gesture. (Semantics test asserts the four custom actions; each has a button or menu path.)
- [x] Seek bar, transport, lyrics scrolling, and sheet interactions are unaffected by the new gesture surface. (The gesture input is scoped to the artwork block; taps below touch slop propagate to child clickables. Full traversal is a manual on-device check.)

## Gotchas

- **Direction metaphor is pinned by product choice**: right = next is the flick-the-card-away metaphor, the reverse of a pager. Implement with anchoredDraggable, not HorizontalPager (a pager's page order fights this mapping and RTL). If on-device feel says flip it, it is two swapped callbacks, not a rework.
- **Axis lock or chaos**: without locking to the initial drag axis, diagonal drags fire both machines. Lock on touch slop, release on settle.
- **The vertical machine owns both up and down.** Two separate draggables on one surface will fight; one machine, three anchors.
- **MediaController does not expose decoder Format**; that is why GET_AUDIO_FORMAT exists. Do not try to read it app-side.
- **Details sheet during track change**: the sheet snapshot is keyed to the mediaId it opened for; if the track changes underneath, update the content, do not dismiss.
- **The peek lives or dies on prefetch.** Artwork decode is async; if the neighbor image is first requested when the finger moves, the peek flashes empty and looks cheap. Resolve and warm both neighbors the moment a transition settles, keyed by queue index, and re-warm on queue mutation. Shuffle re-orders neighbors: read them from the queue's actual order, never from library order.
- **Do not animate under reduced motion "just a little".** Snap means snap; phase 11's audit promises it.

## Handoff

Phase 12 is unchanged: it ships what exists, now including a touch-complete Now Playing. The backlog inherits: library-row swipe actions.
