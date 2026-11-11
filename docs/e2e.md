# End-to-end tests

The end-to-end suite drives the debug app on an emulator through real screens and
asserts what each step does. It is phase 17 (`docs/design-spec/phase-17-end-to-end.md`).
No Mac and no network are involved: a fixture library is loaded straight into the
database and the media root before the app starts.

Slice 1 (this document's current scope) provides the fixture, the harness, the runner,
and three journeys. The tag audit (slice 2), the per-screen crawl tables (slice 3), and
gestures, rotation, and persistence (slice 4) build on it.

## Run it

```
make end-to-end         # E2ESuite: every journey
make end-to-end-smoke   # SmokeSuite: one fixture journey, for a quick check
make end-to-end-report  # open the last HTML report
```

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
| `screenshots/*.png` | One PNG per `screenshot(name)` call, named `<Class>-<name>.png`, for a person to look at; never a pass or fail |
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
  require is the row's identity in tests too.
- **Ids and titles come from the fixture manifest** (`E2ETest.fixture`,
  `trackMediaId(title)`, `episodeMediaId(title)`), not from numbers typed into a test.
- **Every step asserts what it did.** "Did not crash" is never the check.

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
