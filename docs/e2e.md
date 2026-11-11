# End-to-end tests

The end-to-end suite drives the debug app on an emulator through real screens and
asserts what each step does. It is phase 17 (`docs/design-spec/phase-17-end-to-end.md`).
No Mac and no network are involved: a fixture library is loaded straight into the
database and the media root before the app starts.

Slice 1 provides the fixture, the harness, the runner, and three journeys. Slice 2 puts
a stable tag on every control and adds the audits that keep it so. Slice 3 adds a crawl
table per screen that acts on every control and checks what it did, and the nightly
workflow. Slice 4 adds the Now Playing gesture tests, every crawl table again in
landscape, and the settings and queue checked across a real process kill.

## Run it

```
make end-to-end         # E2ESuite (journeys, gestures, widget and notification, audits, crawls in both orientations), then persistence
make end-to-end-smoke   # SmokeSuite: one fixture journey, for a quick check
make end-to-end-audit   # AuditSuite: only the on-device tag audits
make end-to-end-crawl   # CrawlSuite: only the surface crawls (portrait and landscape) and the table coverage check
make end-to-end-persistence  # only the settings and queue across a process kill
make end-to-end-report  # open the last HTML report
make tag-audit          # the source-level tag audit: no device, seconds (also in make lint and CI)
```

To run one class or one test, pass it to the script relative to the app package:
`scripts/e2e/run.sh surfaces.NowPlayingCrawl` or
`scripts/e2e/run.sh "audits.TagCoverageAudit#podcastsShowsAndThePodcastPlayer"`.

**Nightly.** `.github/workflows/e2e.yml` runs `make end-to-end` every night (03:17 UTC)
and on demand (Actions, "End-to-end (nightly)", Run workflow) on an API 36 emulator on
`ubuntu-latest`, which has KVM because the repo is public. It uploads `build/e2e/` as the
`e2e-results` artifact and opens an issue with the summary line when the run fails. It is
never a required check on a pull request.

`scripts/e2e/run.sh` picks the device:

1. The first attached device in the `device` state, **a phone included**. The script
   prints which one it uses and says `PHYSICAL DEVICE` when it is not an emulator. Only
   the debug build (`io.cloudcauldron.bocan.android.debug`) is installed and then
   removed by Gradle, so a release install on the same phone is not touched.
2. With nothing attached, it boots the AVD `bocan-api36` headless (override with
   `make end-to-end AVD=<name>`), waits for boot, runs, and shuts it down again. Set
   `E2E_KEEP_EMULATOR=1` to keep it running between runs.

The emulator boots with `-no-audio`. The tests never assert sound, only player state.

## What a run leaves behind

Everything lands in `build/e2e/`:

| Path | What |
|------|------|
| `report/index.html` | Gradle's HTML report, per class and per test |
| `junit/*.xml` | JUnit XML, the same results for tools |
| `screenshots/*.png` | One PNG per `screenshot(name)` call, named `<Class>-<name>.png`, and one per failed crawl step (`<Class>-<crawl>-step-<n>-failed.png`), for a person to look at; never a pass or fail |
| `summary.txt` | The summary line alone, for tools (the nightly workflow quotes it) |
| `persistence-*.log` | The raw `am instrument` output of each persistence phase |
| `gradle.log` | The full Gradle output |
| `emulator.log` | The emulator's output, when the script booted it |

The script's last lines summarise: suite, device, time, passed, failed, skipped.

## How it is built

- **Fixture library.** `python3 scripts/demo-media/generate.py --fixture` writes
  `app/src/debug/assets/fixture/` (debug builds only). See `scripts/demo-media/README.md`
  for its contents. `FixtureLibrary` (in `app/src/debug/`) seeds it through the same path
  as the phase 14 demo: verified file copies, `SyncApplier`, and the lyrics cache.
- **Profiles.** `FixtureProfile.UnpairedDemo` is a fresh install (the app seeds the demo
  itself), `PairedLibrary` is an unreachable paired Mac plus the fixture, and
  `EmptyPaired` is the paired Mac with an empty library.
- **One clean install per test.** The Android Test Orchestrator runs each test in its own
  instrumentation with `clearPackageData`, so no test sees another's library, settings,
  or queue. The harness checks the install is clean and fails at once if it is not
  (for example, a run from Android Studio without the Orchestrator).
- **Harness** (`app/src/androidTest/.../Harness.kt`): `E2ETest(profile, completeOnboarding)`
  grants notifications, seeds the profile, then launches `MainActivity`. It offers
  `awaitPlayer(...)` (waits on the transport's `PlayerUiState`), `awaitState(...)`
  (waits on anything else, such as a database row), and `screenshot(name)`.
- **Page objects** (`Screens.kt`): one class per screen. Controls are found by their
  stable tag from `E2ETags` (`app/src/main/.../e2e/E2ETags.kt`); rows are found by the
  start of their TalkBack sentence (`Ui.rowTitled("Breakwater 1")`), never by position.

## Rules for test code

- **Wait on state, never on time.** No `Thread.sleep`. Use `awaitPlayer`, `awaitState`,
  or the `Ui` finders, which all wait with a timeout and fail with what they were
  waiting for.
- **Tags are stable strings.** Add a constant to `E2ETags` (`surface.control`) and apply
  it with `Modifier.testTag(...)`. Never derive a tag from a label, a title, or an index.
- **Rows by content description.** The merged TalkBack sentence that the standards
  require is the row's identity in tests too. Repeated controls share one
  `surface.row.control` tag; `Ui.awaitRow(tag, label)` picks one by its TalkBack sentence
  or, for rows without one (list items, chips, menu options), its label.
- **Ids and titles come from the fixture manifest** (`E2ETest.fixture`,
  `trackMediaId(title)`, `episodeMediaId(title)`), not from numbers typed into a test.
- **Every step asserts what it did.** "Did not crash" is never the check.

## Adding a control

Every control a user can tap, long-press, toggle, slide, or type into carries a tag. A
control without one fails two checks:

- `make tag-audit` (`scripts/e2e/audit-test-tags.py`, run by `make lint` and CI) scans
  the sources: each `.clickable`, `.combinedClickable`, `.toggleable`, or `.selectable`
  needs a `testTag` in the same modifier chain, and each Material control call (`Button`,
  `IconButton`, `FilterChip`, `DropdownMenuItem`, `Slider`, `TextField`, ...) needs one
  among its arguments. A control whose own handler is `null` (the visual `Switch` inside a
  toggle row) is skipped. It is a heuristic: for a call site it misreads, put
  `// e2e-audit: ignore (reason)` on that line.
- `TagCoverageAudit` and its siblings (`app/src/androidTest/.../audits/`) are the
  contract. They walk every route in `Destination.kt` and every sheet, menu, and dialog
  reachable without a Mac, and fail on any node in the semantics tree with a click,
  long-click, set-progress, or text-input action and no tag. The failure lists every gap
  with its role, label, text, and bounds. `DestinationCoverageTest` fails when a new
  route is added and no audit visits it.

So, for a new control:

1. Add a constant to `E2ETags` and apply it: `Modifier.testTag(E2ETags.X.Y)`. Settings
   rows (`SettingsToggleRow`, `SettingsNavRow`) and `LabelledSlider` take a required
   `tag` argument instead. A bottom sheet passes `dragHandle = { SheetDragHandle() }`.
2. If it is on a new screen or sheet, add a step to the matching audit so the crawl
   reaches it, and add a new route to that audit's route set.
3. Add a row to that screen's crawl table (`app/src/androidTest/.../surfaces/*Crawl.kt`)
   that acts on the control and states what must then be true. A control no table can
   reach yet (it needs a Mac, say) goes in `CrawlRegistry.NOT_CRAWLED` with the reason
   and the slice that will reach it.

Only a node Material builds and gives no modifier for may go untagged, through an entry
in `audits/TagAllowlist.kt` with its reason. Entries match by structure, never by a label,
and each is anchored to a node the app did tag. Today there are two: the bottom sheet's
drag handle wrapper and its scrim.

## Crawl tables

Each screen has a crawl class in `surfaces/` whose table is a list of rows:

```kotlin
Step(E2ETags.Sync.UNPAIR, Tap, Visible(E2ETags.Sync.UNPAIR_CONFIRM)),
Step(E2ETags.Sync.UNPAIR_CANCEL, Tap, Gone(E2ETags.Sync.UNPAIR_CONFIRM)),
Step(E2ETags.Rows.TRACK, Tap, PlayerState(playing = true, mediaIdPrefix = c.track("Breakwater 2")), row = "Breakwater 2"),
```

A row names the control by its tag (and `row`, its label, when the tag repeats), the
action (`Tap`, `LongPress`, `Type`, `Clear`, `Submit`, `Slide`, `Swipe`, `Reorder`,
`TapLink`, `Back`), and the postcondition the interpreter (`CrawlRunner`) waits for:
`Visible`, `Gone`, `Navigated`, `Shows`, `Text`, `Toggled`, `Flipped`, `Selected`,
`PlayerState`, `Holds` (stored state, such as a setting or a database row), or
`IntentSent` (a stubbed browser link). "Did not crash" is not one of them. Labels come
from the app's string resources and the fixture manifest, never typed into the table.
Tap, long press, swipe, and reorder bring the control on screen first; slides, typing,
and submits go through semantics actions, as TalkBack does, and never scroll.

Tap, long press, swipe, and reorder scroll every scrolling parent in turn until the control
is wholly on screen (a control half under a top bar takes a tap elsewhere; a chip in a
sideways row inside a scrolling page needs both scrolled). A `Visible` check on a row may
scroll its list to it, as a person would. `Look` touches nothing and only waits for its
postcondition, for state the app restored on its own.

`surfaces/LandscapeCrawls.kt` runs every table again, unchanged, after turning the activity
to landscape (the navigation rail carries the bottom bar's tags). A table that passes in
portrait and fails in landscape usually means something the user cannot reach there: the
first landscape runs found the A to Z rail cut off at K, an album opening on its cover with
Play below the fold, and sheets opening at a half-height stop that showed one row.

Two checks keep the tables complete. `CrawlTest.finishCrawl` fails on any tag seen on
screen during a crawl that no table steps on; `TableCoverageTest` fails on any `E2ETags`
constant that no table steps on and `CrawlRegistry` does not explain, and on any
`CrawlRegistry` entry a table does step on. Every crawl also runs the tag audit before
each step.

## Gestures

`gestures/NowPlayingGestureTests.kt` swipes the Now Playing surface with a real finger. Every
distance and speed comes from `PlayerGestureThresholds` and the surface's measured size: a
slow drag past the commit distance (only the distance can commit it), a short fling past the
fling velocity (only the speed can), and drags short of both that must snap back. It also
covers both queue ends, repeat one and repeat all at the ends (a swipe reaches exactly what
Next and Previous reach), two quick flings, the four TalkBack actions, and motion: the block
follows the finger with animations on and stays put under reduced motion.

Reduced motion is the system animator scale. `MotionSetting` sets it in each test and puts the
device's own value back afterwards: the nightly's emulator runs with animations off, a
developer's usually with them on, and a test must not depend on either. A flaky swipe is a
wrong distance or speed, never a reason to retry.

## Persistence

A test runs inside the app's own process, so no test can kill the app and watch it start
again, and the Orchestrator wipes the app's data between tests. `run.sh` therefore runs the
check outside Gradle, after the suite, as two plain `am instrument` runs around a kill:

1. `PersistenceWritePhase` on a cleared install changes every setting a person can change
   through its screen (sorts, last tab, a recent search, every settings section, the whole
   equalizer with a saved preset) and leaves a queue paused on its second track. It fails if
   any setting is still at its default (that setting's persistence would go untested), then
   records what it left in the app's files (`StoredState`).
2. `am force-stop` kills the process and the playback service.
3. `PersistenceReadPhase` starts a new process on the same data, seeds nothing, and checks
   every stored value against the record, the queue restored paused at the same index, and
   the screens showing the restored state.

Both phases skip unless `run.sh` passes `-e e2ePersistence true`. A new setting must join
`StoredState.read` and the write table by hand: nothing detects a setting left out of both.
Once it is in `StoredState.read`, the write phase fails until the table changes it.

## Widget and notification

`system/SystemSurfaceTests.kt` checks what the app shows outside its own window. It never
drives the launcher or System UI, and it never takes a screenshot of them:

- **The widget.** Glance renders through the launcher, so the test reads the state store the
  widget renders from (`WidgetStateStore`). It must follow play, pause, Next, a track with no
  cover, an episode, and a cleared queue.
- **The media notification.** The test finds the app's posted notification that carries a
  media session token, and reads that session with a platform `MediaController`, which is
  how System UI reads it. It checks the title, artist, album, length (the decoded length,
  the same as the app's seek bar), the play state, the cover, and that the notification goes
  away when the queue is cleared.
- **The artwork grant.** System UI loads the cover from this app's provider in its own
  process, so it needs a read grant on the artwork Uri. The test asks the activity manager
  whether System UI's uid may read the Uri the session publishes, and, as a control, that
  the shell's uid may not.

Repeat one is set before anything plays, so a 4 second fixture track loops and does not move
the queue under the test.

## Add a journey

1. Write a class in `app/src/androidTest/.../journeys/` that extends
   `E2ETest(FixtureProfile.X)`.
2. Drive it through the page objects; add a page object method or an `E2ETags` constant
   if a control is missing one.
3. Add the class to `E2ESuite` (and to `SmokeSuite` only if it is quick and central).
4. Run it twice in a row: `make end-to-end` must pass both times without a manual reset.

## Read a failure

- The failure message names what the test waited for and the last state it saw, for
  example `player never reached: next moved to 'Breakwater 2' within 15000 ms; last
  state: PlayerUiState(...)`, or `never found row 'Breakwater' in 'library.albumsGrid'`.
- Open `build/e2e/report/index.html` for the stack trace and the logcat Gradle captured.
- Look at the screenshots taken before the failing step.
- `FixtureRule` failing with "the install is not clean" means the Orchestrator did not
  run: use `make end-to-end`, not a bare `connectedAndroidTest` without it.
- A tag audit failure lists each untagged node as
  `screen: [OnClick] Button, description=[], text=[Reset to normal], bounds=...`. Find the
  control by its text and screen, and tag it (see "Adding a control").
- A crawl failure names the crawl, the step number, the action and tag, what was
  expected, and what was found, for example `crawl 'sync', step 7: tap 'sync.cancel'
  expected 'sync.syncNow' on screen within 10000 ms, found 0 node(s)`, and leaves
  `<Class>-<crawl>-step-<n>-failed.png` in `screenshots/`. Decide first whether the app or
  the table is wrong: slice 3's own first runs found both.
