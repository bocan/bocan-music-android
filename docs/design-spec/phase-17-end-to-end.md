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
  audit-test-tags.py                       // source scan: interactive call sites without a tag (advisory, then failing)
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
  journeys/*.kt                            // first run, play, podcast resume, search, queue, settings persistence
  surfaces/*.kt                            // one table-driven crawl per screen
  gestures/NowPlayingGestureTests.kt       // swipe left, right, up, down; reduced motion snap
  Rotation.kt                              // every screen in landscape once
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
   - `Harness.kt`: `createAndroidComposeRule<MainActivity>()`, `GrantPermissionRule` for notifications, onboarding marked complete unless a journey wants it, `DataStore` files and the database wiped before each class, a `screenshot(name)` helper writing PNGs to the app's external files dir under `e2e/`, and a `waitForIdle` that also waits for the player's state flow.
   - `scripts/e2e/run.sh` and `make end-to-end`: pick the attached device (`adb devices`), else boot `bocan-api36` headless and wait for boot; `./gradlew :app:installDebug :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.cloudcauldron.bocan.app.E2ESuite`; pull `e2e/` screenshots and the JUnit XML into `build/e2e/`; print pass, fail, skipped, and the report path. `make end-to-end-smoke` runs `SmokeSuite`. A missing emulator is a clear error naming `make emulator-headless`.
   - Three journeys: first run (skip onboarding, demo seeds, album shows, play, mini player shows the cover, lock the tab bar and reopen); play from the library (fixture, tap album, tap track, playing state, position advances, next, previous); podcast resume (open the in-progress episode, position matches, play, pause, position persisted after the activity is recreated).
2. **Tags and the audit.** `E2ETags` namespace with `surface.control` names; row-level controls carry `surface.row.control` and rows are found by their content description, never by index. Sweep every screen so every clickable, toggleable, slider, text field, and long-pressable node has a tag. `TagCoverageAudit` visits every destination in `Destination.kt` and every sheet reachable without a Mac, collects nodes with click, long-click, toggle, set-progress, or text-input actions, and fails on any without a tag, except an allowlist in one file with a comment per entry (system dialogs, the navigation bar items Compose Material tags itself). `audit-test-tags.py` is the cheap source-level version wired into `make lint` as a warning first, failing once the sweep lands. Convention documented in `docs/e2e.md` and `CLAUDE.md`: a new control ships with a tag and a crawl-table row.
3. **Surface crawls.** One test class per screen, each a table of `(tag, action, postcondition)` the interpreter runs, so the reviewable artifact is the table: Library (each of the six tabs, each sort menu entry, the shuffle-all button), Album, Artist, Playlist (both kinds and the folder), Genre, Search (typing, each recent search, clearing), Podcasts home (continue-listening shelf, shows grid), Show detail (rows, overflow: mark played and unplayed, show notes with a link), Now Playing (every transport control, seek, shuffle, repeat cycle, speed cycle, sleep timer, lyrics toggle and offset, chapters sheet, song details sheet, queue sheet with reorder and remove), Settings root and each section (every toggle flips, every picker sets, About links), Sync status in paired and unpaired states (Sync now reaches "unreachable" and stops, Unpair confirm and cancel, Remove media confirm and cancel, the demo remove and reload), Pairing (searching state, back), Equalizer (each band slider set, each preset, bass boost, reset). Postcondition discipline: "did not crash" is never a postcondition. After every crawl, a coverage check compares the tags seen on that screen with the tags its table names and fails on any gap.
4. **Gestures, rotation, persistence.** Now Playing swipes using `performTouchInput` with distances and velocities read from `PlayerGestures` thresholds: left advances, right goes back, up opens details, down dismisses; a sub-threshold drag snaps back; reduced motion (set through the harness) snaps without animation. Every screen once in landscape with the same table run in a reduced form (the navigation rail and side-by-side Now Playing from phase 11). Persistence: every setting toggled in slice 3 is read back after the process is killed and relaunched (`am force-stop`, then a fresh rule), and the queue restores paused at the same index.
5. **Widget and notification state (stretch).** Assert the widget state store after play, pause, and track change; assert the notification's media session metadata through `MediaController` from the test process; assert the System UI read grant exists for the artwork Uri (phase 16 of this repo's fixes). Nothing drives the launcher.
6. **Hermetic sync (stretch).** A loopback fake Mac in the test process on MockWebServer with TLS, reusing `HeldDeviceIdentity` and the pairing vectors from the sync unit tests; a debug-only endpoint override so discovery is bypassed; a journey that pairs, syncs the fixture manifest over the wire, watches the progress notification, and confirms the library appears. This is the only slice that talks to a socket, and it is the loopback interface.

The nightly workflow (`e2e.yml`) lands with slice 3: `workflow_dispatch` plus a nightly cron, a Linux runner with KVM, `reactivecircus/android-emulator-runner` at API 36, `make end-to-end`, screenshots and XML uploaded as artifacts, failures opened as an issue with the summary. It is never a required check.

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
data class Step(val tag: String, val action: Action, val expect: Postcondition)
sealed interface Action { data object Tap; data object LongPress; data class Type(val text: String); data class Slide(val fraction: Float); data class Swipe(val direction: Direction) }
sealed interface Postcondition { data class Visible(val tag: String); data class Text(val tag: String, val contains: String); data class Toggled(val tag: String, val on: Boolean); data class Navigated(val screen: String); data class PlayerState(val playing: Boolean?, val mediaIdPrefix: String?); data class SheetDismissed(val tag: String) }
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

- [ ] `make end-to-end` runs the full suite on an attached emulator and writes screenshots and a summary to `build/e2e/`; `make end-to-end-smoke` runs the subset.
- [ ] The fixture library loads without a Mac and every surface has content: all six library tabs, both playlist kinds and the folder, genres, two shows, chapters, lyrics of both kinds, the paired sync screen.
- [ ] Every actionable node on every screen carries a tag; the audit proves it and fails otherwise.
- [ ] Every tag is exercised by a crawl table with a postcondition; the coverage check proves it and fails otherwise.
- [ ] All four Now Playing swipes and the snap-back are tested with thresholds read from the gesture code; reduced motion is tested.
- [ ] Every setting survives a process kill; the queue restores paused at the same index.
- [ ] Every screen renders in landscape and its reduced table passes.
- [ ] The nightly workflow runs the same target on a Linux emulator and uploads artifacts; it is not a required PR check.
- [ ] `docs/e2e.md` explains how to run it, how to add a control (tag plus table row), and how to read a failure; `CLAUDE.md` carries the tag rule.
- [ ] Slices 5 and 6 are either done or explicitly deferred with a dated note here.

## Gotchas

- **Compose test tags are invisible to the accessibility tree by default.** That is fine for `ui-test` finders, which read the semantics tree, but if UiAutomator must find a Compose control (system dialog handling aside), enable `testTagsAsResourceId` on the root only in debug. Do not leak resource ids into release.
- **Swipes must beat the gesture thresholds.** A default `swipeLeft()` may be too short or too slow for the phase 13 commit rules; read the commit distance and velocity from `PlayerGestures` and swipe beyond them. A flaky swipe test is a wrong threshold, not a retry candidate.
- **The demo's first-launch seed and the fixture race.** The harness must set the demo's auto-seeded flag before launching in the `PairedLibrary` profile, or the demo library seeds on top of the fixture. `UnpairedDemo` is the one profile that wants that seed.
- **Permissions on API 33 and above.** Notifications need `POST_NOTIFICATIONS`; grant it through the rule, never by tapping the system dialog. On an emulator that carries the Android 17 local network permission, grant it the same way or the pairing screen blocks.
- **Emulator audio.** Boot with `-no-audio`; the player still reaches the playing state and advances position on the null audio device. If a future emulator refuses, assert on the transport state flow, which is what the tests do anyway.
- **Package data between classes.** Use the Android Test Orchestrator with `clearPackageData` so each class starts clean, and the harness re-seeds. Without it, one failing class poisons the next.
- **KVM on GitHub-hosted runners.** Public repos get KVM on `ubuntu-latest`; if this repo is private, the nightly needs a larger runner or a self-hosted one. Decide when slice 3 lands and record it here.
- **Do not test the widget by screenshot.** Glance renders through the launcher; assert the state store and stop.
- **No em dashes or en dashes** in tags, tables, messages, or docs.

## Handoff

From here a control that ships without a tag or a table row fails the build, so the "every button clicked" guarantee holds without anyone remembering it. New phases add rows to the tables as part of their acceptance. The hermetic fake Mac, if built, becomes the place to test protocol changes end to end before the Mac side ships them.
