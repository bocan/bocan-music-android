# Phase 07: Podcasts

> Depends on: phase-04-playback-engine.md, phase-05-library-ui.md, phase-06-now-playing-queue.md
> Read docs/design-spec/_standards.md and sync-protocol.md (sections 6 through 8) first.
> Provides: the Podcasts tab over synced episodes, podcast playback behaviours, and the podcast Now Playing variant.

## Goal

Everything you expect from listening to podcasts, over the episodes your Mac already downloaded and synced: shows grid, episode lists with progress, per-episode resume, per-show speed, skip intervals, chapters, a continue-listening shelf, and show notes. Subscribing, searching for shows, and downloading happen on the Mac; the phone is the pocket player.

## Non-goals

- No feed fetching, no subscribing, no episode downloading from the internet. If it is not in the manifest, it does not exist here.
- No transcripts in v1 (the Mac caches them, but the protocol does not expose them yet; candidate for protocol v1 additive endpoint later).
- No OPML anything.

## Outcome shape

```
app/src/main/kotlin/io/cloudcauldron/bocan/app/podcasts/
  PodcastsHomeScreen.kt      // continue-listening shelf + shows grid
  ShowDetailScreen.kt        // episodes list + show description
  EpisodeRow.kt              // artwork, title, date, duration, progress arc, played state
  ShowNotesSheet.kt          // rendered HTML show notes
  ChaptersSheet.kt
  PodcastsViewModel.kt ShowDetailViewModel.kt
core/playback/src/main/kotlin/io/cloudcauldron/bocan/playback/podcast/
  EpisodePlaybackRules.kt    // resume, mark-played, position write-back policy
  ChaptersRepository.kt      // fetch + cache /v1/chapters/{episodeId}
  ChapterModel.kt
```

## Definitions and contracts

- **Resume rules** (`EpisodePlaybackRules`, mirroring the Mac's phase 21-5):
  - Loading an episode seeks to `episode_state.playPositionMs` if `playState == inProgress` and position is between 5 s and duration minus 15 s; otherwise starts at 0.
  - Position writes back every 5 s while playing and on pause/stop/transition/service-destroy.
  - Completion (within 15 s of the end or natural end) sets `playState = played`, `completedAt`, clears position.
  - A played episode restarted from the list resets to `inProgress` at 0.
  - The global music resume path must skip episodes (two resume paths must not fight; same trap the Mac spec calls out).
- **Speed**: effective speed = `episode_state.speedOverride` if set, else the show's `defaultSpeed` from the manifest, else the app-wide podcast default (settings, 1.0x initial). Setting speed from the podcast transport writes `speedOverride` for that episode's show scope: store per-show override locally in a small `podcast_prefs` DataStore map keyed by podcastId (do not write to synced tables). Music playback speed is untouched by podcast speed.
- **Skip intervals**: back 15 s, forward 30 s (settings-adjustable: 10/15/30/45/60). The podcast transport row replaces previous/next with skip-back/skip-forward; previous/next remain reachable via the queue.
- **Chapters**: `ChaptersRepository` fetches Podcasting 2.0 chapters JSON from the Mac when `hasChapters`, parses `{title, startTime, endTime?, img?, url?}` entries, caches by episodeId + sha of body. Now Playing shows the current chapter title under the episode title; the ChaptersSheet lists chapters with tap-to-seek and highlights the active one.
- **Scrobbling**: episodes emit stats events flagged `isPodcast = true`; phase 09 must skip them (parity with the Mac).

## Implementation plan

1. `PodcastsHomeScreen`: continue-listening shelf (horizontal cards: artwork, title, remaining time, progress ring; from `PodcastDao.observeContinueListening()`), then subscribed-shows grid with unplayed-count badges (count of `unplayed` episodes per show).
2. `ShowDetailScreen`: header (artwork, title, author, expandable description), episode list newest-first with `EpisodeRow` states: unplayed dot, in-progress arc with remaining time ("23 min left"), played checkmark and dimming. Tap plays; long-press: Play next, Add to queue, Mark as played / Mark as unplayed (these two write only phone-local `episode_state`; allowed), Show notes.
3. Show notes: sanitize-render `descriptionHtml` (strip scripts/styles, allow basic tags and links; links open in Custom Tabs with a confirmation for non-https). Reachable from episode long-press and from Now Playing overflow.
4. `EpisodePlaybackRules` wired into the player listener set from phase 04; the 20-minute music heuristic from phase 04 is disabled for episode media ids.
5. Podcast Now Playing variant: when the current item is an episode, the transport swaps to skip-back / play-pause / skip-forward, a speed chip (cycles presets, long-press for the stepper), and the chapter line. Artwork falls back to show artwork.
6. Chapters repository + sheet.
7. Settings additions (Podcasts section): default speed, skip intervals, "resume seeded from Mac" explanation row.

## Context7 lookups

- use context7: Media3 MediaSession custom command buttons for skip intervals in the notification
- use context7: Compose rendering sanitized HTML text with links

## Dependencies

None new beyond phases 04-06 (HTML rendering via `AndroidView` + `TextView`/`Html.fromHtml` or a small trusted renderer; no WebView for show notes).

## Test plan

### EpisodePlaybackRules (JVM, virtual time)
- Resume matrix: unplayed -> 0; inProgress at 3 s -> 0 (below floor); inProgress mid -> seek; inProgress at duration-10 s -> 0-restart guard; played -> 0 and flips to inProgress.
- Write-back cadence: 5 s ticks recorded; pause writes immediately; completion marks played and clears position.
- Seeded state: first sync's seed respected, later Mac positions ignored (integration with phase 01 seeding, fixture-driven).
- Speed resolution precedence: episode override > show default > app default.

### Chapters
- Parser fixtures: full spec sample, missing endTimes, out-of-order start times (sorted), garbage entries skipped.
- Active-chapter resolution at positions: boundaries inclusive of start, exclusive of end.

### UI
- Continue-listening ordering and exclusion of played episodes (Turbine over fixture DB).
- Unplayed badges compute correctly; mark-played updates them reactively.
- Show notes sanitizer: script tags stripped, links preserved (unit-test the sanitizer as a pure function).

## Acceptance criteria

- [ ] Episodes resume where the phone last left them; fresh episodes seeded from the Mac's position exactly once.
- [ ] Per-show speed sticks across episodes of that show and never affects music.
- [ ] Skip intervals work from screen, notification, and Bluetooth (notification custom commands may land fully in phase 10; screen and headset hooks work now).
- [ ] Chapters list, tap-to-seek, and live current-chapter label work on an episode with chapters.
- [ ] Continue-listening shelf reflects reality and updates live.
- [ ] Mark played/unplayed works and syncs never overwrite it.
- [ ] Podcasts are excluded from scrobble-eligible events (flag proven by test).
- [ ] Show notes render safely; scripts stripped, external links confirmed.

## Gotchas

- **Two resume paths must not fight** (the Mac spec's own words): the music restore, the episode resume, and any queue-restore position must have a single owner per item type. Episodes: `EpisodePlaybackRules` wins, always.
- **Refresh never writes state**: sync applies content; `episode_state` belongs to the phone. If a test shows a sync moving a position, the bug is critical.
- **Duration from the manifest may disagree with the decoded file** by a second or two; completion detection uses the player's reported duration, not the manifest's.
- **HTML show notes are untrusted input** from arbitrary feeds, relayed by the Mac. Sanitize as if hostile.

## Handoff

Phase 09 assumes the `isPodcast` flag on stats events. Phase 10 assumes podcast skip commands are expressible as session custom commands for the notification and Auto.
