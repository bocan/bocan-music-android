# Phase 17: Whole-App End-to-End on an Emulator

> Depends on: phase-14-demo-library.md (the seeding path the fixture library reuses), phase-16-trunk-and-releases.md (every slice lands as a PR with the new gates; the nightly workflow follows its no-secrets rule), phase-13-gestures.md (the gestures under test).
> Read docs/design-spec/_standards.md first. The Mac repo's E2E programme (`../bocan-music/docs/design-spec/ADR-079` to `ADR-086`) is the reference design; this phase ports its ideas to Compose and an Android emulator.
> Provides: `make end-to-end`, a fixture library loaded without a Mac, a stable test tag on every control with an audit that fails when one is missing, and suites that tap, toggle, drag, and swipe every control on every screen with a defined postcondition.

## Goal

`make end-to-end`, run with an emulator attached, drives the debug app through every screen and every control and asserts what each one does: a tap opens the sheet it should, a toggle flips and persists, a swipe changes the track, a search finds the song, a podcast resumes where it stopped. No Mac is involved. A fixture library big enough to exercise every surface (letters, genres, folders, playlists of both kinds, shows with chapters, lyrics, a CUE clip) is loaded straight into the database and media root through the same path the demo album uses. "Every control" is enforced, not hoped for: an audit crawls the semantics tree of every screen and fails on any actionable node that has no stable tag, and a second check fails on any tagged control that no crawl table exercises.

## Non-goals

- No audio audibility assertions. The tests assert player state (playing, position advancing, current item), never sound.
- No pixel assertions. Screenshots are captured per screen as run artifacts for a person to look at, never as pass or fail.
- No real Mac, no mDNS, no real network. Sync against a loopback fake Mac is a stretch slice (6) and is the only network in the programme; the internet is never touched.
- No Android Auto head unit driving. The browse tree is unit-tested (phases 10 and 15); the DHU stays a manual gate.
- No home-screen widget driving. Glance widgets cannot be driven from Compose tests; the widget's state store is asserted instead.
- No testing on a physical phone by default. The target aims at an emulator; a phone works if attached, and the target says which it is using.

## Outcome shape

```
Makefile                                   // end-to-end, end-to-end-smoke, end-to-end-report, emulator-wait
scripts/e2e/
  run.sh                                   // find or boot the emulator, seed, run the suite, pull artifacts, summarise
  wait-for-boot.sh                         // adb wait-for-device plus sys.boot_completed polling
  audit-test-tags.py                       // source scan: interactive call sites without a tag (make tag-audit, failing)
  test_audit_test_tags.py                  // its unit tests, negative cases included
scripts/demo-media/generate.py             // gains --fixture: writes app/src/debug/assets/fixture/ (silence tracks, covers, lyrics, chapters, manifest)
app/src/debug/assets/fixture/              // the fixture library, debug builds only, mirrors the media root layout
app/src/debug/kotlin/io/cloudcauldron/bocan/app/fixtures/
  FixtureLibrary.kt                        // seeds the fixture profile through SyncApplier; debug source set only
  FixtureProfile.kt                        // UnpairedDemo, PairedLibrary, EmptyPaired
app/src/main/kotlin/io/cloudcauldron/bocan/app/e2e/
  E2ETags.kt                               // the tag namespace: surface.control, surface.row.control
app/src/main/kotlin/**                     // Modifier.testTag(E2ETags.x) on every interactive control
app/src/androidTest/kotlin/io/cloudcauldron/bocan/app/
  Harness.kt                               // compose rule over MainActivity, fixture seeding, permissions, screenshots
  Screens.kt                               // page objects: one object per screen, finders by tag
  audits/TagCoverageAudit.kt               // crawls every screen: actionable nodes must be tagged; tags must be in a table
  audits/TagCoverageAuditFirstRun.kt, TagCoverageAuditEmpty.kt  // the same crawl for the unpaired and empty profiles
  audits/TagAudit.kt, AuditTest.kt         // the node collector and the crawl base class
  audits/TagAllowlist.kt                   // the only untagged nodes allowed, each with its reason
  audits/DestinationCoverageTest.kt        // the audits between them reach every route in Destination.kt
  AuditSuite.kt                            // the audits alone (make end-to-end-audit)
  journeys/*.kt                            // first run, play, podcast resume, search, queue, settings persistence
  surfaces/*.kt                            // one table-driven crawl per screen
  gestures/NowPlayingGestureTests.kt       // swipe left, right, up, down; reduced motion snap
  gestures/MotionSetting.kt                // the animator scale per test, restored after
  surfaces/LandscapeCrawls.kt              // every crawl table again in landscape
  persistence/PersistencePhases.kt, StoredState.kt  // write, am force-stop, read: settings and queue across a kill
  system/SystemSurfaceTests.kt             // slice 5: the widget state store, the notification's session, System UI's artwork grant
  sync/FakeMac.kt, FakeMacRule.kt          // slice 6: the loopback Mac, written from sync-protocol.md
  sync/FakeMacVectorsTest.kt               // the fake Mac's code and proof against the shared golden vectors
  sync/HermeticSyncJourney.kt              // pair from Settings, sync over the wire, pins, hashes, lyrics, the progress notification
  surfaces/PairingCrawl.kt                 // the welcome flow's pairing and first-sync tables (and LandscapePairingCrawl)
app/src/debug/.../fixtures/DiscoveryOverride.kt  // slice 6: the fake Mac's advert in place of mDNS; the release twin passes mDNS through
  E2ESuite.kt, SmokeSuite.kt               // JUnit suites the Makefile targets name
.github/workflows/e2e.yml                  // nightly and manual: emulator on a Linux runner, artifacts uploaded
docs/e2e.md                                // how to run, how to add a control, how to read a failure
docs/release-checklist.md                  // "make end-to-end green on the release commit" as a gate
```

## What carries over from previous specs

- `DemoLibrary` (phase 14): copy verified files under the media root, apply a manifest through `SyncApplier`, mark downloaded, seed lyrics, serve chapters from assets. `FixtureLibrary` reuses `DemoFiles` and the same order of operations with a bigger manifest and a different asset root, and adds the paired-server row for the `PairedLibrary` profile.
- `scripts/demo-media/generate.py` (phase 14): tone tracks, covers, LRC, chapters, manifest with hashes. The `--fixture` mode reuses every function with a different table of tracks and writes to the debug asset root.
- `MediaLayout`, `ArtworkStore`, `SyncApplier` (phases 01, 03): the only write paths. The fixture never touches a DAO for a synced table.
- The Compose UI test dependencies (phase 00 and 05): `ui-test-junit4` and `ui-test-manifest` are already in the catalog; they move from `testImplementation` to also `androidTestImplementation`.
- `PlayerGestures` (phase 13): the thresholds and velocities the swipe tests must exceed live there; the tests read them rather than guessing.
- The two-tier CI shape (phase 16): the E2E workflow is a third, nightly file, and never a required PR check.

## Implementation plan

Six slices, each one branch and one PR, in order. Slices 1 to 4 are the deliverable; 5 and 6 are stretch.

1. **Fixture mode and the harness.**
   - `generate.py --fixture` writes `app/src/debug/assets/fixture/`: 48 tracks of 4 seconds of near-silence at 64 kbps mono (about 32 KB each, under 2 MB total) across 14 artists chosen to cover letter buckets (`A`, `The Beatles` style leading article, `Édith`, a digit, a symbol), 18 albums across 4 genres, 2 discs on one album, a CUE-style clip pair, ReplayGain on all, artwork on most and deliberately absent on two, lyrics on six tracks (synced on four, unsynced on two), one manual playlist, one smart playlist, one playlist folder with a child, 2 shows with 3 episodes between them, one episode with chapters and one already `inProgress` at 40 percent. Every id is above the demo's `ID_BASE` plus one million so the two libraries can never collide.
   - `FixtureLibrary.seed(profile)` in the debug source set. `UnpairedDemo` is the phase 14 path untouched. `PairedLibrary` inserts a `sync_server` row (fake fingerprint, unreachable) and seeds the fixture, so every paired-only control renders. `EmptyPaired` inserts the row and nothing else. Seeding is idempotent and finishes before the activity launches.
   - `Harness.kt`: `createAndroidComposeRule<MainActivity>()` (the Compose `v2` test API), `GrantPermissionRule` for notifications, onboarding marked complete unless a journey wants it, a clean install per test (the Orchestrator's `clearPackageData` wipes the database, DataStore, and media root; the harness verifies the install is clean and fails at once if not, rather than deleting files under a live `AppGraph`), a `screenshot(name)` helper writing PNGs through the androidx Test Storage service (the app's external files dir is wiped by `clearPackageData`, Test Storage output is not and Gradle pulls it to the host), and `awaitPlayer` / `awaitState` waits on the player's state flow and on database rows.
   - `scripts/e2e/run.sh` and `make end-to-end`: pick the attached device (`adb devices`), else boot `bocan-api36` headless and wait for boot; `./gradlew :app:installDebug :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.cloudcauldron.bocan.app.E2ESuite`; pull `e2e/` screenshots and the JUnit XML into `build/e2e/`; print pass, fail, skipped, and the report path. `make end-to-end-smoke` runs `SmokeSuite`. A missing emulator is a clear error naming `make emulator-headless`.
   - Three journeys: first run (skip onboarding, demo seeds, album shows, play, mini player shows the cover, send the app to the background and reopen it, and the mini player still holds the track); play from the library (fixture, tap album, tap track, playing state, position advances, next, previous); podcast resume (open the in-progress episode, position matches, play, pause, position persisted after the activity is recreated).
2. **Tags and the audit.** `E2ETags` namespace with `surface.control` names; row-level controls carry `surface.row.control` and rows are found by their content description, never by index. Sweep every screen so every clickable, toggleable, slider, text field, and long-pressable node has a tag. `TagCoverageAudit` visits every destination in `Destination.kt` and every sheet reachable without a Mac, collects nodes with click, long-click, toggle, set-progress, or text-input actions, and fails on any without a tag, except an allowlist in one file with a comment per entry (nodes Material builds inside a component and gives no modifier for). `audit-test-tags.py` is the cheap source-level version, wired into `make lint`, `make ci`, and the CI workflow as `make tag-audit`; it fails from the start because the sweep lands in the same slice. Convention documented in `docs/e2e.md` and `CLAUDE.md`: a new control ships with a tag and a crawl-table row.
3. **Surface crawls.** One test class per screen, each a table of `(tag, action, postcondition)` the interpreter runs, so the reviewable artifact is the table: Library (each of the six tabs, each sort menu entry, the shuffle-all button), Album, Artist, Playlist (both kinds and the folder), Genre, Search (typing, each recent search, clearing), Podcasts home (continue-listening shelf, shows grid), Show detail (rows, overflow: mark played and unplayed, show notes with a link), Now Playing (every transport control, seek, shuffle, repeat cycle, speed cycle, sleep timer, lyrics toggle and offset, chapters sheet, song details sheet, queue sheet with reorder and remove), Settings root and each section (every toggle flips, every picker sets, About links), Sync status in paired and unpaired states (Sync now reaches "unreachable" and stops, Unpair confirm and cancel, Remove media confirm and cancel, the demo remove and reload), Pairing (searching state, back), Equalizer (each band slider set, each preset, bass boost, reset). Postcondition discipline: "did not crash" is never a postcondition. After every crawl, a coverage check compares the tags seen on that screen with the tags its table names and fails on any gap.
4. **Gestures, rotation, persistence.** Now Playing swipes using `performTouchInput` with distances and velocities read from `PlayerGestures` thresholds: left advances, right goes back, up opens details, down dismisses; a sub-threshold drag snaps back; reduced motion (set through the harness) snaps without animation. Every screen once in landscape with the same tables run whole (the navigation rail and side-by-side Now Playing from phase 11). Persistence: every setting a person can change is read back after the process is killed and relaunched (`am force-stop`, then a new instrumentation on the same data, since a test cannot outlive its own process), and the queue restores paused at the same index.
5. **Widget and notification state (stretch).** Assert the widget state store after play, pause, and track change; assert the notification's media session metadata through a platform `MediaController` on the session token the posted notification carries, as System UI reads it; assert the System UI read grant exists for the artwork Uri (phase 16 of this repo's fixes). Nothing drives the launcher.
6. **Hermetic sync (stretch).** A loopback fake Mac in the test process on MockWebServer with mutual TLS, its own `HeldCertificate` identity (`HeldDeviceIdentity` lives in `:core:sync`'s unit tests, out of the app's reach), and the pairing vectors from the sync unit tests packed into the test APK; a debug-only discovery override so mDNS is bypassed; a journey that pairs, syncs the fixture manifest over the wire, watches the progress notification, and confirms the library appears. This is the only slice that talks to a socket, and it is the loopback interface.

**Slice 1 notes (2026-11-11).** Landed on `feat/e2e-slice-1-harness`. Where it differs from the text above, the text above was corrected: the Test Storage screenshots, the verify-not-wipe harness, and the first-run journey's "background and reopen" step. Other facts the next slices rely on:
- Fixture episodes are 2 minutes at 32 kbps, not the track length, so the in-progress episode (48 s, 40 percent) sits inside the app's resume window (5 s from the start to 15 s from the end) with room to play on without "completing".
- `lyrics/kinds.json` records each lyrics file's wire kind (synced or unsynced), since the Mac normally sends it.
- Fixture episode chapters are generated but not served yet: the app reads chapters from the demo assets (ids starting `demo-episode-`) or the Mac. Slice 3 decides how the debug build serves fixture chapters before the chapters sheet crawl.
- Tags are looked up in the unmerged semantics tree; a tagged child of a clickable parent (the mini player cover) has no tag in the merged tree.
- `E2ETags` holds only the slice 1 controls (onboarding, navigation, library tabs, mini player, transport). Slice 2 sweeps the rest.
- `make emulator` and `make emulator-headless` now default to `bocan-api36`.
- UiAutomator is not added yet; it comes with the first system-dialog test.

**Slice 2 notes (2026-11-11).** Landed on `feat/e2e-slice-2-tags`, stacked on slice 1. Where it differs from the text above, the text above was corrected (the allowlist's contents, `make tag-audit` failing from the start and running in CI). Facts the next slices rely on:
- `E2ETags` covers every control in `app/src/main` (Glance widget aside). Repeated controls share one tag (`Rows.*`, `Sort.OPTION`, preset and option chips, `Equalizer.BAND`, `Library.ALPHABET_LETTER`); `Ui.awaitRow(tag, label)` picks one by its TalkBack sentence or label. Per-provider scrobble controls are `E2ETags.Scrobble.provider(ProviderId.X, CONTROL)`.
- `SettingsToggleRow`, `SettingsNavRow`, and `LabelledSlider` take a required `tag`, so a new setting cannot compile without one. Every `ModalBottomSheet` passes `dragHandle = { SheetDragHandle() }`; the nine pushed screens share `components/BackButton` (`Common.BACK`).
- The audits run in four classes: `TagCoverageAudit` (PairedLibrary: library tabs, sort menus, the four detail routes, the track actions sheet, folders, Now Playing with its overflow, lyrics, speed, details, queue with the clear dialog, sleep timer idle and armed, the equalizer with the preamp and save dialog, podcasts, show, episode menu, show notes, the podcast player, search results and recent searches, settings and all six sections with the unpair, remove media, and token dialogs), `TagCoverageAuditFirstRun` (UnpairedDemo: onboarding, unpaired sync with the demo, pairing while searching, the demo episode's chapters sheet), `TagCoverageAuditEmpty` (EmptyPaired empty states), and `DestinationCoverageTest`. They join `E2ESuite`; `AuditSuite` runs them alone.
- `TagAllowlist` has two entries, both from `ModalBottomSheet`: the two nodes wrapping the drag handle (they hold its tap and long press; the `dragHandle` slot only reaches the handle inside) and the full-window scrim. Both match by structure and are anchored to a node tagged `Common.SHEET_HANDLE`, so a sheet without the shared handle still fails. The navigation bar items need no entry: slice 1 tagged them.
- Not reached on the device, so covered by `make tag-audit` only: the onboarding first-sync page, the pairing code entry and Mac rows, sync in progress (Cancel), the Android 17 local network notice, the insecure show-notes link dialog (no fixture show notes carry a link), scrobble dead-letter rows and the Last.fm finish button, and a user preset's delete icon. Slice 3 or 6 reaches them when a table needs them.
- `run.sh` now clears Gradle's previous results first; before, a build that failed before the tests ran reported the last run's results as its own.

The nightly workflow (`e2e.yml`) lands with slice 3: `workflow_dispatch` plus a nightly cron, a Linux runner with KVM, `reactivecircus/android-emulator-runner` at API 36, `make end-to-end`, screenshots and XML uploaded as artifacts, failures opened as an issue with the summary. It is never a required check.

**Slice 3 notes (2026-11-11).** Landed on `feat/e2e-slice-3-crawls`, stacked on slice 2. Facts the next slices rely on:
- Eleven crawl classes in `surfaces/` (library, detail screens, search, podcasts, podcast player, now playing, settings, equalizer, sync, first run, empty library), each one test running one table through `CrawlRunner`, which audits tags before every step. `CrawlRegistry` lists them all; `TableCoverageTest` is the static coverage check and `CrawlTest.finishCrawl` the runtime one. `CrawlSuite` and `make end-to-end-crawl` run them alone; `E2ESuite` runs journeys, `AuditSuite`, and `CrawlSuite`.
- Fixture chapters: debug builds serve `fixture-episode-*` chapters from `assets/fixture/chapters/` through `Context.fixtureChaptersFetcher()` (debug source set; the release source set's twin returns null), wired after the demo's source in `AppGraph`. Night Shift Pilot's show notes carry an https and an http link on `example.invalid`.
- External intents are stubbed with Espresso-Intents (`androidx.test.espresso:espresso-intents`, the catalog's Espresso version); `IntentSent` checks them. No crawl leaves the app or touches the network.
- Touch actions (tap, long press, swipe, reorder) scroll the control on screen first; slides, typing, and submits use semantics actions and never scroll. Scrolling a rotated EQ band slider into view in its horizontal row left the app never idle, so the crawl hung.
- `Ui.awaitRow` scrolls lazy lists to a row not yet composed and tries matchers most specific first (own label, then a child's, the parent's, a sibling's): an episode row's sentence sits on its info block, and every list row is a sibling of every other.
- A failed step leaves `<Class>-<crawl>-step-<n>-failed.png`; `run.sh` writes `build/e2e/summary.txt` and finds the SDK through `ANDROID_HOME` on a runner.
- The crawls found two app bugs, fixed in this slice: one swipe on a queue row removed that row and the next (the removal ran inside `confirmValueChange`, which fires more than once per swipe); and Cancel did nothing while Sync now waited up to 20 s for discovery (only the engine run was cancelled, not the pending launch).
- Not crawled, each with its reason in `CrawlRegistry.NOT_CRAWLED`: the pairing code, Mac row, done and start over, and the welcome flow's first-sync page (slice 6's fake Mac); the Android 17 local network notice (API 36 suite); the scrobble token save, Last.fm sign-in, and dead-letter rows (the internet); the Now Playing gesture surface (slice 4).
- `E2ESuite` passed 25 of 25 twice in a row on `bocan-api36`, about 155 s each.

**Slice 4 notes (2026-11-11).** Landed on `feat/e2e-slice-4-gestures`, stacked on slice 3. Where it differs from the text above, the text above was corrected (landscape runs the tables whole, not reduced; persistence is two instrumentation runs). Facts the next slices rely on:
- `gestures/NowPlayingGestureTests` (12 tests) derives every distance and speed from `PlayerGestureThresholds` and the surface's measured size: slow drags past the commit distance, flings short of it past the fling velocity, drags short of both, both queue ends, repeat one and repeat all at the ends, two quick flings, the four TalkBack actions, and motion on and off. `MotionSetting` sets the animator scale per test and restores the device's own: the nightly's emulator runs with animations off (`disable-animations: true`), a developer's usually on.
- The Now Playing crawl table swipes the gesture surface each way, so `NowPlaying.GESTURES` left `CrawlRegistry.NOT_CRAWLED` (11 entries remain, all slice 6, the internet, or Android 17).
- `surfaces/LandscapeCrawls.kt` (three classes, 11 tests, in `CrawlSuite`) runs every table unchanged after `E2ETest.rotateToLandscape()`. The runner now scrolls every scrolling ancestor in turn until a touched control is wholly on screen, and a `Visible` row check scrolls to its row.
- Persistence is outside Gradle: `run.sh` installs the APKs again, `pm clear`, runs `PersistenceWritePhase` with `am instrument -e e2ePersistence true`, `am force-stop`, then `PersistenceReadPhase` (`keepInstall = true`, `KeptInstallRule`, nothing seeded). The write phase fails if any key of `StoredState` is still at its default. `make end-to-end` runs it after `E2ESuite`; `make end-to-end-persistence` alone. Crawl tables gained `Look`.
- The slice found six app faults, each fixed in its own commit: under repeat all the swipes did not wrap at the queue's ends though the buttons do; the A to Z rail indexed titles under every sort, cut off at K in landscape, and did nothing on a letter without songs; an album in landscape opened on its cover with Play below the fold; the bottom sheets opened at a half-height strip in landscape; and, from the persistence check, the saved queue never came back on a cold start (nothing connected to the playback service until the first play, which replaced it).
- A slice 3 table flaw is fixed: the queue reorder row could move the last row down, a no-op, and still pass.
- Seen, not fixed (outside this phase): the system media controls' resume card cannot reconnect (`MediaResumeListener: onConnectFailed`), because `onPlaybackResumption` is not implemented; Media3 leaves playback resumption off without it.

**Slice 5 notes (2026-11-11).** Landed on `feat/e2e-slice-5-widget`, stacked on slice 4. Facts the next slice relies on:
- `system/SystemSurfaceTests` (6 tests, in `E2ESuite`) checks the widget, the notification, and the artwork grant. It sets repeat one before anything plays, so a 4 second fixture track loops instead of moving the queue.
- The widget is checked through `WidgetStateStore`, the file Glance renders from: play, pause, Next, a track with no cover (Signal), an episode (`isPodcast`), and the empty state after the queue is cleared.
- The notification is read the way System UI reads it, not through the app's own objects: the app's active notification that carries `Notification.EXTRA_MEDIA_SESSION`, then a platform `android.media.session.MediaController` on that token. This replaces the Media3 `MediaController` the slice text named, which would read the session but not prove the notification carries it. Checked: title, artist, album, length, play state, the cover (the notification's large icon, present for Breakwater and dropped for Signal), and the notification removed when the queue is cleared.
- The session's length is the decoded length (4048 ms for a 4000 ms fixture MP3, from the encoder padding), the same value as the app's seek bar; the test compares it with the player, not the manifest.
- The grant is checked with `Context.checkUriPermission` for System UI's uid on the artwork Uri the session publishes, with the shell's uid as the control that must be refused.
- No app fault found. Media3 drops the old cover when the next track has none, and removes the notification when the queue is cleared.

**Slice 6 notes (2026-11-11).** Landed on `feat/e2e-slice-6-fake-mac`, stacked on slice 5. Where it differs from the text above, the text above was corrected (the fake Mac's identity, the vectors as test assets). Facts a later phase relies on:
- `sync/FakeMac` is the server half of `sync-protocol.md` v1, written from the document: mutual TLS (any client certificate in pairing mode, then only the paired phone's), its own code derivation and proof check, every section 6 endpoint with `ETag`, `If-Match`, open `Range`, and the error envelope, and a `pm=0` advert once paired. `FakeMacVectorsTest` checks its code and proof against `pairing-vectors.json`, which `app/build.gradle.kts` adds to the androidTest assets from `:core:sync`, never copied. A protocol change can be tried end to end here before the Mac side ships it.
- Checked against `../bocan-music` before building (2026-11-11): the Mac repo keeps no copy of `sync-protocol.md`; its `pairing-vectors.json` is byte-identical; `Manifest.swift` and the TXT keys are unchanged since the last contract edit (2026-09-07). The Mac now serves `/v1/chapters` (it answered 404 before), which the contract already listed, and `/v1/ping` answers `500 internal` when its server id is unreadable.
- `slowFiles()` trickles bodies at 128 bytes a second, a slow Mac rather than a dead one. A request held before its headers is not usable: the phone gives up after its read timeout and reports the Mac unreachable.
- Discovery: `MacDiscovery.create` takes an optional browser wrapper; the debug build's `DiscoveryOverride` replaces mDNS with the fake Mac's advert while one is set, and the app's TXT parsing still runs on it. `FixtureProfile.Unpaired` starts with no Mac, no library, and the demo's seed spent.
- The slice found five app faults, each fixed in its own commit:
  - Cancel (and Sync later) could leave the sync running. `SyncCoordinator.cancelSync` cancelled the launch waiting in `SyncEngine.syncNow` first; that ran `syncNow`'s `finally`, which cleared `activeJob` and opened the run lock, sometimes before `engine.cancel()` read `activeJob`. The run went on with no owner and the lock open. `syncNow` now stops its run when its caller is cancelled and waits for it before opening the lock (unit test: `cancelling the caller of syncNow stops the run before the call returns`, which failed before the fix with the run still `Transferring`). The race came from this phase's own slice 3 fix to Cancel.
  - A cancelled sync started again by itself when discovery first saw the paired Mac after the cancel: the sync-on-discovery trigger had not fired yet in that process, so nothing debounced it. A cancel now snoozes the trigger for its 15 minute window (`SyncTriggers.snooze`); Sync now and the periodic worker are unaffected.
  - Syncs ran without their foreground service. `SyncForegroundService` stopped on the first terminal state it saw, and a sync started from rest finds the state still `Idle` (or `Done`, `Failed`, unreachable from the last one) when it starts (log: `sync.service.stopping state=Idle` 78 ms after the start). So such a sync, which is nearly every sync, never showed its progress notification or Cancel action, held its wake and Wi-Fi locks, or ran as a foreground service. The service now lives until the runs it launched complete (`SyncHost.launchSync` returns the run's `Job`).
  - A second wrong pairing code left the first wrong digits in the field and announced nothing: `Failed(CodeMismatch)` equalled the previous state, so the state flow never emitted it. `CodeMismatch` now carries the attempts left, and the screen keys the code entry on it.
  - The pairing screen's Done button could not be used: both hosts leave the moment pairing succeeds. It is removed with its tag and string.
- `CrawlRegistry.NOT_CRAWLED` now holds 5 tags, each needing the internet or Android 17 (deferred below).
- Seen in the code, not fixed and not tested: a request still waiting for its headers is not cancelled until the read timeout, because `Downloader` and `SyncApi` make blocking `execute()` calls that coroutine cancellation does not reach (the downloader checks for cancellation only between body chunks). Calling `Call.cancel()` from the coroutine's cancellation would make Cancel take effect at once.

## Definitions and contracts

```kotlin
// app/src/debug/kotlin/.../fixtures/FixtureProfile.kt
enum class FixtureProfile { UnpairedDemo, PairedLibrary, EmptyPaired }

// app/src/debug/kotlin/.../fixtures/FixtureLibrary.kt
class FixtureLibrary(assets: DemoAssets, store: DemoLibrary.Store, dispatchers: CoroutineDispatchers) {
    suspend fun seed(profile: FixtureProfile)      // idempotent; wipes nothing; the harness wipes
    companion object { const val ID_BASE = DemoLibrary.ID_BASE + 1_000_000L }
}

// app/src/main/kotlin/.../e2e/E2ETags.kt
object E2ETags {
    object Library { const val TAB_ALBUMS = "library.tab.albums"; const val SHUFFLE_ALL = "library.shuffleAll"; ... }
    object NowPlaying { const val PLAY_PAUSE = "nowPlaying.playPause"; const val SEEK = "nowPlaying.seek"; ... }
    fun row(surface: String, control: String) = "$surface.row.$control"
}

// app/src/androidTest/kotlin/.../surfaces/CrawlTable.kt
data class Step(val tag: String, val action: Action, val expect: Postcondition, val row: String? = null, val timeoutMs: Long = 10_000)
sealed interface Action { Tap; LongPress; Type(text); Clear; Submit; Slide(fraction); Swipe(direction); Reorder(rows); TapLink(text); Back; Look }
sealed interface Postcondition { Visible(tag, row?); Gone(tag, row?); Navigated(screen, markerTag); Shows(text); Text(tag, contains);
    Toggled(tag, on); Flipped; Selected(tag, row?); PlayerState(playing?, mediaIdPrefix?, what, check?); Holds(what, check); IntentSent(action, uri) }
// Gone replaces SheetDismissed; Navigated names a marker tag only that screen has; Holds reads stored state (DataStore, the database).
```

Rules:

- **Tags are stable strings, never derived from titles or indices.** Renaming a control's label must not break a test; removing a control must.
- **Rows are found by content description.** The merged TalkBack sentence from the standards ("Title, Artist, Album, Duration") is the row's identity in tests too, so accessibility and testability are one thing.
- **Every step asserts a postcondition.** The interpreter refuses a table row whose postcondition is absent.
- **The audit is the contract for "every control."** A control without a tag fails the audit; a tag without a table row fails the coverage check; both run in `make end-to-end` and in the nightly.
- **Fixture data is generated, checked in, and hashed** like the demo, and lives only in the debug source set. The release APK carries none of it.
- **Tests wait on state, never on time.** `waitUntil` with a condition on the semantics tree or the player state flow; no `Thread.sleep`.

## Context7 lookups

- use context7: Compose ui-test createAndroidComposeRule performTouchInput swipeLeft swipeWithVelocity
- use context7: Compose ui-test onAllNodes hasClickAction hasSetProgressAction SemanticsMatcher testTag
- use context7: Compose ui-test waitUntil waitUntilExactlyOneExists idling and mainClock
- use context7: androidx.test GrantPermissionRule and UiAutomator for system dialogs
- use context7: Gradle connectedDebugAndroidTest testInstrumentationRunnerArguments class and Android Test Orchestrator clearPackageData
- use context7: reactivecircus/android-emulator-runner API 36 KVM on ubuntu-latest
- use context7: Compose testTag and testTagsAsResourceId for accessibility tree visibility

## Dependencies

Catalog additions: `androidx.test:runner`, `androidx.test:rules`, `androidx.test.ext:junit`, `androidx.test.uiautomator:uiautomator` (system dialogs only), `androidx.test:orchestrator` (per-class package data clearing). Compose `ui-test-junit4` added to `androidTestImplementation`. An AVD: `bocan-api36` exists on the development Mac; the run script names it and `make emulator-headless` boots it. Nothing new for release builds.

## Test plan

The phase is the test plan. What must be true of the suite itself:

- `make end-to-end` on a freshly booted `bocan-api36` finishes green in under 25 minutes; `make end-to-end-smoke` in under 6.
- The tag audit and the coverage check each have a negative test: a scratch branch with one untagged button fails the audit; a scratch branch with one tag removed from a table fails the coverage check.
- Each journey runs twice in a row on the same emulator without a manual reset and passes both times (the harness's wipe works).
- A deliberate wrong postcondition in a table fails with a message naming the tag, the action, and what was expected versus found.
- No test contains `Thread.sleep`, a hard-coded pixel coordinate, or an index-based row lookup; `audit-test-tags.py` greps for the first two.

## Acceptance criteria

- [x] `make end-to-end` runs the full suite on an attached emulator and writes screenshots and a summary to `build/e2e/`; `make end-to-end-smoke` runs the subset.
      Evidence (2026-11-11, slice 1, `bocan-api36` on the dev Mac): a cold run booted the AVD headless and ran E2ESuite; after the mini player tag fix, four consecutive `make end-to-end` runs passed 3 of 3 (about 15 s each) with no manual reset, writing `report/`, `junit/`, 5 screenshots, and the summary line; `make end-to-end-smoke` passed 1 of 1. The suite holds only the three slice 1 journeys so far.
- [x] The fixture library loads without a Mac and every surface has content: all six library tabs, both playlist kinds and the folder, genres, two shows, chapters, lyrics of both kinds, the paired sync screen.
      Evidence (2026-11-11, slice 3, `bocan-api36`): `FixtureLibraryTests` proves the rows; on the emulator `LibraryCrawl` shows content on all six tabs and the unsynced lyrics of Heather 1, `DetailCrawl` plays Fixture Mix (manual), Loved Fixture Tracks (smart), and Folder Child (inside Fixture Folder) and opens the Folk genre, `PodcastsCrawl` opens both shows, `PodcastPlayerCrawl` seeks by the fixture episode's chapters, `NowPlayingCrawl` seeks by a synced lyrics line, and `SyncCrawl` drives the paired sync screen.
- [x] Every actionable node on every screen carries a tag; the audit proves it and fails otherwise.
      Evidence (2026-11-11, slice 2, `bocan-api36`): `make end-to-end-audit` passed 8 of 8 (`DestinationCoverageTest` plus the three audit classes, every route in `Destination.kt` visited); `make tag-audit` reports no finding. Negative run: with the tag removed from the speed sheet's Reset button, `make tag-audit` failed naming `SpeedSheet.kt:75` and `TagCoverageAudit#nowPlayingItsSheetsAndTheEqualizer` failed with `speed sheet: [OnClick] Button, text=[Reset to normal]`; the tag was restored. States only a Mac or rarer data reach are covered by the source audit alone (see the slice 2 notes).
- [x] Every tag is exercised by a crawl table with a postcondition; the coverage check proves it and fails otherwise.
      Evidence (2026-11-11, slices 3 to 6, `bocan-api36`): the coverage check exists in both halves and fails when it should (negative run: with the Speed Reset row removed, `TableCoverageTest` failed naming `speed.reset`; the row was restored). Slice 4 crawled the gesture surface; slice 6's `PairingCrawl` crawls the Mac row, the code field, Start over, and the first-sync page's Done and Sync later, and removed the pairing screen's Done button, which no host could show. Ticked under the rule this box set in slice 3 (empty `NOT_CRAWLED` or defer it with a dated note): **deferred, 2026-11-11**, the 5 tags left in `CrawlRegistry.NOT_CRAWLED`, each with its reason there. Four need the internet, which the suite never touches (the scrobble token save, Last.fm sign-in, and the two dead-letter actions, which need a scrobble a connected provider refused); one needs Android 17 (the local network notice; the suite runs on API 36). Revisit them when the suite gains an API 37 image or a fake scrobble provider.
- [x] All four Now Playing swipes and the snap-back are tested with thresholds read from the gesture code; reduced motion is tested.
      Evidence (2026-11-11, slice 4, `bocan-api36`): `NowPlayingGestureTests` passed 12 of 12 on its first run and in two full `make end-to-end` runs; distances come from `PlayerGestureThresholds.COMMIT_FRACTION` and `VERTICAL_COMMIT_DP`, speeds from `FLING_VELOCITY`. Negative run: with the repeat-all fix to the neighbours removed, `underRepeatAllTheEndsWrapLikeTheButtons` failed ("'Long Field 1' current after a left swipe on the last track under repeat all", the player still on Long Field 6); the fix was restored.
- [x] Every setting survives a process kill; the queue restores paused at the same index.
      Evidence (2026-11-11, slice 4, `bocan-api36`): the write phase, `am force-stop`, and the read phase passed in two consecutive `make end-to-end` runs, over 21 stored keys (the write phase fails if any is left at its default) plus the queue paused at index 1. Before the connect fix the read phase failed with every setting intact and the player empty (`current=null, queue=[]`), which is the bug that fix closes.
- [x] Every screen renders in landscape and its table passes.
      Evidence (2026-11-11, slice 4, `bocan-api36`): `LandscapeCrawl` (9), `LandscapeFirstRunCrawl`, and `LandscapeEmptyLibraryCrawl` pass in `CrawlSuite`, running every table whole, not reduced. Their first run failed 5 of 11, which led to four app fixes (rail, album header, sheets) and runner changes for nested scrolling.
- [ ] The nightly workflow runs the same target on a Linux emulator and uploads artifacts; it is not a required PR check.
      Partly, not ticked: `.github/workflows/e2e.yml` is written (slice 3) but has not run; a scheduled or dispatched workflow runs only once it is on `main`. Tick with the first green run's link.
- [x] `docs/e2e.md` explains how to run it, how to add a control (tag plus table row), and how to read a failure; `CLAUDE.md` carries the tag rule.
      Evidence (2026-11-11, slice 3): `docs/e2e.md` has "Run it" (with the nightly), "Adding a control" (tag, audit step, table row), "Crawl tables", and "Read a failure" (audit and crawl failures); `CLAUDE.md` carries the tag and table-row rule.
- [x] Slices 5 and 6 are either done or explicitly deferred with a dated note here.
      Evidence: slice 5 is done (2026-11-11, `bocan-api36`): `SystemSurfaceTests` passed 6 of 6. Negative runs: without the System UI grant in `PlaybackService.onCreate`, `systemUiMayReadTheArtworkTheSessionPublishes` failed (`expected:<0> but was:<-1>`); with `WidgetState.fromPlayer` always playing, `theWidgetFollowsPlayPauseAndTrackChanges` failed at the pause; both were restored. Slice 6 is done (2026-11-11, `bocan-api36`): `FakeMacVectorsTest`, `HermeticSyncJourney` (2), `PairingCrawl` (2), and `LandscapePairingCrawl` (2) pass, and two consecutive `make end-to-end` runs passed 63 of 63 with persistence. Negative runs: with the old service stop rule, the progress notification test failed (no notification within 15 s); without the keyed code entry, `crawlToSyncLater` failed at step 6 with the wrong digits still in the field; the engine's cancel test failed before its fix with the run still `Transferring`. Each was restored.

## Gotchas

- **Compose test tags are invisible to the accessibility tree by default.** That is fine for `ui-test` finders, which read the semantics tree, but if UiAutomator must find a Compose control (system dialog handling aside), enable `testTagsAsResourceId` on the root only in debug. Do not leak resource ids into release.
- **Swipes must beat the gesture thresholds.** A default `swipeLeft()` may be too short or too slow for the phase 13 commit rules; read the commit distance and velocity from `PlayerGestures` and swipe beyond them. A flaky swipe test is a wrong threshold, not a retry candidate.
- **The demo's first-launch seed and the fixture race.** The harness must set the demo's auto-seeded flag before launching in the `PairedLibrary` profile, or the demo library seeds on top of the fixture. `UnpairedDemo` is the one profile that wants that seed.
- **Permissions on API 33 and above.** Notifications need `POST_NOTIFICATIONS`; grant it through the rule, never by tapping the system dialog. On an emulator that carries the Android 17 local network permission, grant it the same way or the pairing screen blocks.
- **Emulator audio.** Boot with `-no-audio`; the player still reaches the playing state and advances position on the null audio device. If a future emulator refuses, assert on the transport state flow, which is what the tests do anyway.
- **Package data between tests.** The Android Test Orchestrator with `clearPackageData` gives every test (not only every class) a fresh process on a cleared install, and the harness re-seeds. Without it, one failing test poisons the next; the harness refuses to run on a dirty install.
- **KVM on GitHub-hosted runners.** Public repos get KVM on `ubuntu-latest`; if this repo is private, the nightly needs a larger runner or a self-hosted one. Decided 2026-11-11 (slice 3): the repo is public, so `e2e.yml` runs on `ubuntu-latest` with the udev rule that opens `/dev/kvm`. Revisit if the repo ever goes private.
- **Do not test the widget by screenshot.** Glance renders through the launcher; assert the state store and stop.
- **No em dashes or en dashes** in tags, tables, messages, or docs.

## Handoff

From here a control that ships without a tag or a table row fails the build, so the "every button clicked" guarantee holds without anyone remembering it. New phases add rows to the tables as part of their acceptance. The hermetic fake Mac, if built, becomes the place to test protocol changes end to end before the Mac side ships them.
