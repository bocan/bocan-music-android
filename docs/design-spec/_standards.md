# Standards

Every phase assumes these. Re-read once, then obey without being asked. This is the Android sibling of `bocan-music/docs/design-spec/_standards.md`; where the platforms differ, this file wins for this repo.

## Language and platform

- Kotlin 2.x, JVM toolchain 21. No Java sources.
- `minSdk 29` (Android 10), `targetSdk` = latest stable, `compileSdk` = latest stable.
- Jetpack Compose with Material 3 for all UI. No XML layouts (XML is allowed only where the platform demands it: manifests, widget metadata, `strings.xml`, drawable resources).
- Coroutines and Flow everywhere. No RxJava, no LiveData, no AsyncTask.
- kotlinx.serialization for JSON. No Gson, no Moshi.
- Version catalog (`gradle/libs.versions.toml`) is the single source of dependency versions.
- Before adding or upgrading any dependency, look up the current version and API with Context7 (see the Context7 section). Always choose the latest stable release and avoid deprecated APIs.

## Module layout

Gradle modules with a strict DAG, no upward or sideways imports:

```
:core:observability -> :core:persistence -> :core:sync, :core:playback, :core:scrobble -> :app
```

- `:core:*` modules are Android libraries with no Compose dependency (`:core:playback` may depend on Media3; none may depend on `:app`).
- `:app` is the only module with Compose screens, navigation, widgets, and services' UI surfaces.
- Kotlin package root is `io.cloudcauldron.bocan`, one subpackage per module (`io.cloudcauldron.bocan.sync`, etc.).
- `applicationId` is `io.cloudcauldron.bocan.android`.
- An upward edge means the abstraction is in the wrong layer. Lift it down, do not import up.

## Dependency injection

Manual constructor injection. A single `AppGraph` class in `:app` wires the object graph at startup and hands dependencies to services and view models via factories. No Hilt, no Koin, no Dagger. Every class takes its collaborators as constructor parameters with interfaces at module boundaries so tests can pass fakes.

## Concurrency

- Long-lived mutable state lives in classes that confine mutation to a single dispatcher or use `Mutex`, exposed as `StateFlow` / `Flow`. No `synchronized` blocks in new code.
- Public suspend APIs are main-safe: they switch to the right dispatcher internally. Callers never need `withContext` to call them safely.
- Every long loop and every Flow collector that does per-item work calls `ensureActive()` or cooperates with cancellation.
- `Dispatchers` are injected (a `CoroutineDispatchers` holder), never referenced statically outside it, so tests can use a `TestDispatcher`.
- View models expose `StateFlow<UiState>` and accept events via plain functions. UI state classes are immutable data classes.

## Error handling

- One sealed `*Error` hierarchy per module (`SyncError`, `PlaybackError`, ...), each case carrying context (URL, path, cause, human-readable reason). No bare `Exception` throwing across module boundaries.
- No swallowed exceptions: every `catch` either rethrows a typed error or logs a warning with the cause. `runCatching` without inspection of the failure is banned.
- `error()` / `check()` / `require()` for programmer errors only, never for expected runtime conditions.

## Logging

- `AppLog` facade in `:core:observability`, backed by Timber. Never `println`, never raw `android.util.Log`.
- Categories: `app`, `sync`, `pairing`, `persistence`, `playback`, `podcast`, `scrobble`, `ui`, `network`.
- Pattern: `log.debug("op.start", mapOf(...))`, `log.debug("op.end", mapOf("ms" to elapsed))`, `log.error("op.failed", mapOf("error" to err.toString()))`.
- Keys in `AppLog.sensitiveKeys` (token, sessionKey, password, authorization, apiKey, code, proof) are redacted automatically before emission.
- Release builds log warnings and errors only; debug builds log everything.

## Testing

- JUnit and kotlin.test for JVM unit tests; Robolectric only where an Android API is unavoidable; Turbine for Flow assertions; MockWebServer for HTTP; `androidx.compose.ui.test` for the few interaction tests that need it.
- Tests must not hit the network. Ever. MockWebServer counts as local.
- Fixtures are checked in under `src/test/resources/fixtures/`, deterministic, never generated at test time. Protocol fixtures (manifest JSON, pairing transcripts) are shared golden files: the Mac repo tests against byte-identical copies.
- Every public function of a `:core` module has at least one test. Every bug fix starts with a failing regression test.
- Coverage via Kover: 80 percent line coverage floor per `:core` module, CI-enforced. `:app` (Compose UI) is exempt from the floor but not from tests of its view models.
- Property-style tests for interesting algebra: the manifest differ, the shuffle strategies, the LRC parser.

## Lint and format

- ktlint (via the Gradle plugin) and detekt with configs at repo root; Android Lint fatal on `NewApi`, `MissingPermission`.
- CI fails on any lint or format diff. A pre-commit hook (installed by `scripts/install-hooks.sh`, created in phase 00) runs both on changed files.

## Strings and localization

- Every user-facing string lives in `:app`'s `res/values/strings.xml` with a stable snake_case key. No bare string literals in composables, notifications, or widgets. This mirrors the Mac repo's `no_bare_user_facing_literal` rule; enforce it with a detekt rule scoped to `:app` composable files.
- Dates, numbers, and durations are formatted with `java.text` / `android.text.format` formatters, never string concatenation.
- English (`values/`) is the source locale. A pseudolocale QA pass uses Android's built-in `en-XA` (enable `pseudoLocalesEnabled true` for debug builds).

## Security and privacy

- No analytics, no telemetry, no crash reporting that leaves the device. Nothing phones home except the sync connection to the paired Mac and, if the user opts in, scrobble providers.
- The device TLS private key lives in the Android Keystore, non-exportable. Peer certificates and pairing state live in app-private storage.
- Scrobble credentials go in `EncryptedSharedPreferences` or the Keystore-wrapped equivalent, never plain SharedPreferences, never the Room DB.
- Synced media lives in app-specific external storage (`getExternalFilesDir`). No `MANAGE_EXTERNAL_STORAGE`, no broad storage permissions. The app never modifies a synced file.
- Cleartext traffic is disabled. The sync client speaks TLS only, with its own trust evaluation (pinned peer cert), never the system trust store.

## Performance baselines

- Cold launch to interactive library under 1.5 s on a mid-range 2023 device.
- A 10,000-track list scrolls at 60 fps (LazyColumn with stable keys, no per-row allocation storms).
- Seek latency under 100 ms for local files.
- Idle battery: zero wakelocks held while paused; the foreground service stops when playback stops and sync is idle.

## Accessibility

- Every interactive element has a `contentDescription` or is marked decorative. Track rows merge their children into one TalkBack sentence: "Title, Artist, Album, Duration".
- Transport controls expose state ("Play" vs "Pause"). Sliders report values textually ("80 hertz, plus 3 decibels").
- Honour system font scale (sp everywhere), reduced motion, and high-contrast settings.

## Commits and PRs

- Conventional Commits, scope = module name without the `:core:` prefix: `feat(sync): ...`, `fix(playback): ...`, `chore(deps): ...`.
- One logical change per commit and PR. Every PR links its phase file.
- Never use em dashes or en dashes anywhere: not in code, comments, commit messages, markdown, or UI copy. Use commas, colons, parentheses, or plain hyphens.
- No AI attribution trailers of any kind in commits or PRs.

## Context7

Add `use context7` to any implementation prompt touching evolving APIs (Media3, Room, WorkManager, Compose, OkHttp, Coil, Glance). Each phase lists its expected lookups. Always target the latest stable version; if a phase file pins a version that is no longer latest, prefer latest stable and note the deviation in the PR.

## What "done" means

1. Every acceptance-criteria box in the phase file is ticked.
2. `./gradlew check test koverVerify` is green locally.
3. CI is green.
4. The phase's Handoff contract is honoured.
5. No `TODO(phase-NN)` markers remain for the completed phase.
