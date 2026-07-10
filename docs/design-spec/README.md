# Bòcan for Android: Design Spec

This directory is the complete build plan for the Android companion app. It follows the same system as `bocan-music/docs/design-spec/` on the Mac side: one phase per file, each phase independently implementable in a fresh session by a capable coding model.

## How to use

1. Start a fresh session per phase file. Paste or reference exactly one phase.
2. Read `_standards.md` first, always. It is binding on all code.
3. Phases that touch the wire protocol also require `sync-protocol.md`. That file is the contract; the Mac-side server (see `phase-mac-1-sync-server.md`) implements the same document.
4. Implement in order unless a phase's `Depends on:` header says otherwise.
5. A phase is done when every acceptance-criteria box is ticked, `./gradlew check test` is green, and the Handoff contract is honoured.

## Section headings every phase uses

Goal, Non-goals, Outcome shape, Implementation plan, Definitions and contracts, Context7 lookups, Dependencies, Test plan, Acceptance criteria, Gotchas, Handoff.

## Phase index

| File | Scope |
|------|-------|
| `_standards.md` | Cross-cutting rules: language, modules, concurrency, errors, logging, testing, strings, commits. Read first, then obey without being asked. |
| `sync-protocol.md` | The Bòcan Sync Protocol v1 contract: discovery, pairing math, mutual TLS, manifest schema, file transfer. Shared with the Mac repo. |
| `phase-00-foundations.md` | Repo that compiles and launches an empty Compose shell: Gradle modules, CI, lint, logging, theming tokens, adaptive app icon from `assets/icons/`. No features. |
| `phase-01-persistence.md` | Room schema mirroring the sync manifest, plus phone-local state tables, DAOs, FTS search, reactive Flows. Data layer only. |
| `phase-02-discovery-pairing.md` | Find the Mac via mDNS, generate a device identity (P-256 key + self-signed cert), run the pairing ceremony, persist the trust store. |
| `phase-03-sync-engine.md` | Manifest fetch and diff, resumable verified downloads, deletes, transactional DB apply, auto-sync triggers, foreground service, sync status UI. |
| `phase-04-playback-engine.md` | Media3 ExoPlayer with FFmpeg extension, `MediaLibraryService`, gapless queue, ReplayGain, speed with pitch correction, resume, audio focus. |
| `phase-05-library-ui.md` | Compose shell: navigation, Artists / Albums / Songs / Genres / Playlists / Folders browsing, search, mini player bar, album and artist detail. |
| `phase-06-now-playing-queue.md` | Full Now Playing screen, queue sheet, shuffle (including smart shuffle), repeat, synced lyrics view, sleep timer with fade. |
| `phase-07-podcasts.md` | Podcasts tab over synced episodes: per-episode resume, per-show speed, chapters, continue-listening shelf, show notes. |
| `phase-08-eq-effects.md` | 10-band EQ with presets, bass boost, ReplayGain mode UI, skip silence, crossfade (documented approach and limits). |
| `phase-09-scrobbling.md` | Last.fm, ListenBrainz, and Rocksky scrobbling with an offline-resilient queue, mirroring the Mac's rules. |
| `phase-10-system-integration.md` | Android Auto, Glance home-screen widget, Bluetooth/AVRCP metadata, media notification polish, app shortcuts. |
| `phase-11-polish.md` | Material You theming audit, TalkBack accessibility pass, localization scaffolding, settings surface, onboarding flow. |
| `phase-12-release.md` | CI/CD on GitHub Actions, signing, versioning, Play Store and F-Droid readiness, privacy declarations. |
| `phase-mac-1-sync-server.md` | The Mac side: a new `SyncServer` module in the `bocan-music` repo. Bonjour advertising, pairing UI in Settings, manifest generation from GRDB, file serving. Implemented over there, specified here so the contract stays in one place. |

## Feature parity map (Mac feature -> Android disposition)

| Mac feature | Android |
|-------------|---------|
| Gapless playback | Yes, ExoPlayer (phase 04) |
| Crossfade 0-12 s | Best effort, documented limits (phase 08) |
| 10-band EQ, bass boost, presets | Yes via DynamicsProcessing (phase 08) |
| ReplayGain track/album | Yes, values come from the manifest (phase 04) |
| Playback speed with pitch correction | Yes (phase 04) |
| Smart + manual playlists, folders, accent colors | Read-only: smart lists are materialized by the Mac at manifest time (phases 01, 05) |
| FTS search | Yes, Room 3 FTS5 on the bundled SQLite driver (phases 01, 05) |
| Synced lyrics (LRC) | Yes, served by the Mac per track (phase 06) |
| Ratings, loved, play stats | Ratings and loved display synced values; play stats are phone-local (phases 01, 06) |
| Podcasts: resume, speed, chapters, show notes | Yes over synced episodes; no subscribing on the phone (phase 07) |
| Scrobbling: Last.fm, ListenBrainz, Rocksky | Yes (phase 09) |
| Sleep timer with fade | Yes (phase 06) |
| CUE virtual tracks | Yes via Media3 ClippingConfiguration (phases 01, 04) |
| Wide format support (APE, WavPack, DSD, Musepack...) | Yes via the Media3 FFmpeg extension, decoded to PCM (phase 04) |
| Tag editing, fingerprinting, duplicate tools | Never. The phone edits nothing. |
| Subsonic / remote servers | Out of scope by design |
| Visualizers | Out of scope by design |
| Internet radio | Out of scope (local sync only) |
| Android-specific extras | Android Auto, widget, Material You, Bluetooth autoplay, media notification (phase 10) |
