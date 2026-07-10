# Phase 00: Foundations

> Depends on: nothing. This is the first phase.
> Read docs/design-spec/_standards.md first.
> Provides: a repo that compiles, launches an empty themed Compose shell, and has CI, lint, tests, coverage, logging, and the app icon in place.

## Goal

A skeleton that makes every later phase boring: Gradle multi-module build, the module DAG, the logging facade, theme tokens, the adaptive app icon built from the Bòcan brand assets, a green CI pipeline, and pre-commit hooks. Launching the app shows a themed empty screen with the Bòcan name. No features.

## Non-goals

- No database, no networking, no playback, no real screens.
- No dependency that a later phase does not explicitly need yet (add Media3, Room, OkHttp in their own phases).

## Outcome shape

```
bocan-music-android/
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  gradle/wrapper/...
  .editorconfig
  .gitignore
  detekt.yml
  scripts/install-hooks.sh
  .github/workflows/ci.yml
  core/observability/
    build.gradle.kts
    src/main/kotlin/io/cloudcauldron/bocan/observability/AppLog.kt
    src/main/kotlin/io/cloudcauldron/bocan/observability/LogCategory.kt
    src/test/kotlin/io/cloudcauldron/bocan/observability/AppLogTests.kt
  core/persistence/   (empty library module, wired into the DAG)
  core/sync/          (empty library module)
  core/playback/      (empty library module)
  core/scrobble/      (empty library module)
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/kotlin/io/cloudcauldron/bocan/app/BocanApplication.kt
    src/main/kotlin/io/cloudcauldron/bocan/app/AppGraph.kt
    src/main/kotlin/io/cloudcauldron/bocan/app/MainActivity.kt
    src/main/kotlin/io/cloudcauldron/bocan/app/theme/{Theme.kt, Color.kt, Type.kt}
    src/main/res/values/strings.xml
    src/main/res/mipmap-anydpi-v26/ic_launcher.xml
    src/main/res/drawable/{ic_launcher_foreground.xml, ic_launcher_background.xml}
    src/main/res/values/ic_launcher_colors.xml
```

## Implementation plan

1. Bootstrap Gradle: Kotlin DSL, version catalog, Kotlin 2.x, AGP latest stable, JVM toolchain 17, `minSdk 29`, `compileSdk`/`targetSdk` latest stable. Enable Compose only in `:app`.
2. Create the five library modules and `:app` with the DAG from `_standards.md` expressed as Gradle dependencies. Add an architecture test (a small unit test in `:app` that parses `settings.gradle.kts` module list and each module's declared project dependencies via a generated report, or use the `build.gradle.kts` constraints) OR simply rely on Gradle: a lower module must not declare a dependency on a higher one. Keep it enforced by convention plus code review; do not add heavyweight arch-test frameworks.
3. `:core:observability`: `AppLog` facade over Timber. API:

   ```kotlin
   enum class LogCategory { App, Sync, Pairing, Persistence, Playback, Podcast, Scrobble, Ui, Network }

   interface AppLog {
       fun debug(event: String, fields: Map<String, Any?> = emptyMap())
       fun info(event: String, fields: Map<String, Any?> = emptyMap())
       fun warning(event: String, fields: Map<String, Any?> = emptyMap())
       fun error(event: String, fields: Map<String, Any?> = emptyMap())
       companion object {
           val sensitiveKeys = setOf("token", "sessionKey", "password", "authorization", "apiKey", "code", "proof")
           fun forCategory(category: LogCategory): AppLog
       }
   }
   ```

   Values whose key is in `sensitiveKeys` render as `<redacted>`. Debug builds plant a `Timber.DebugTree`; release plants a tree that drops debug/info.
4. Theme: Material 3 with dynamic color (Material You) when available (API 31+), falling back to the Bòcan brand palette. Brand seed colors from the Mac app: accent light `#236AD4`, accent dark `#4C8DFF`; dark background family from the website (`#1A1024`, `#241634`) as the fallback dark scheme's surfaces. Support light and dark. Typography: system default with a scale matching Material 3 defaults.
5. App icon: build an Android adaptive icon from `assets/icons/AppIcon.icon/Assets/` (SVG layers: `1-moon.svg`, `2-spirit.svg`, `3-face.svg`, `4-guitar.svg`).
   - Foreground: convert the composed spirit/face/guitar layers to a single VectorDrawable (Android Studio's SVG importer or `svg2vector`), scaled into the adaptive-icon safe zone (66 percent circle).
   - Background: a VectorDrawable gradient approximating the Mac icon's dark gradient (deep indigo `#0E0A26` to `#293380`) with the moon layer.
   - Provide a monochrome layer (API 33 themed icons) from the face outline.
   - If SVG conversion of a layer fails, rasterize `assets/icons/favicon.svg` at 432x432 for the foreground as a fallback and note it as debt.
6. `MainActivity` + `BocanApplication` + empty `AppGraph`. The activity shows a centered "Bòcan" wordmark on the themed background. Edge-to-edge enabled.
7. Lint and format: ktlint Gradle plugin, detekt with a repo-root `detekt.yml` (default rules plus `MaxLineLength` 140), Android Lint with `NewApi` and `MissingPermission` fatal. `scripts/install-hooks.sh` writes a `.git/hooks/pre-commit` that runs ktlint and detekt on staged Kotlin files.
8. Kover wired with an 80 percent verify rule on `:core:observability` (the only core module with code so far); later phases extend the rule to their modules.
9. CI (`.github/workflows/ci.yml`): on push and PR, set up JDK 17 + Gradle cache, run `./gradlew check test koverVerify assembleDebug`. No emulator jobs in this phase.
10. `.gitignore` for Android/Gradle/IDE. Commit the Gradle wrapper.

## Context7 lookups

- use context7: latest stable Android Gradle Plugin and Kotlin plugin versions and compatibility matrix
- use context7: Jetpack Compose BOM latest stable and Material 3 dynamic color setup
- use context7: adaptive icon with monochrome layer, mipmap-anydpi-v26 XML format
- use context7: Kover Gradle plugin coverage verification setup

## Dependencies

Compose BOM + Material 3, Activity Compose, Timber, ktlint plugin, detekt, Kover. Nothing else.

## Test plan

- `AppLogTests`: redaction of every sensitive key; non-sensitive fields pass through; event strings are emitted verbatim; category tag appears.
- A launch smoke test with Robolectric: `MainActivity` inflates without crashing in light and dark.

## Acceptance criteria

- [ ] `./gradlew assembleDebug` produces an installable APK showing the themed empty shell with the Bòcan adaptive icon in the launcher.
- [ ] Module DAG exists exactly as specified; no module depends upward.
- [ ] `AppLog` redacts sensitive keys, proven by test.
- [ ] ktlint, detekt, Android Lint, and Kover verify all pass via `./gradlew check test koverVerify`.
- [ ] CI workflow runs the same and is green.
- [ ] Pre-commit hook installs and blocks a deliberately misformatted file.
- [ ] App icon renders correctly at API 29 (legacy fallback), API 31 (adaptive), and API 33+ (themed monochrome).

## Gotchas

- **Do not enable Compose in core modules.** It bloats build times and violates the layering; only `:app` composes UI.
- **The adaptive-icon safe zone is smaller than you think.** The visible circle is the middle 66 percent; test on a round-mask launcher before ticking the icon box.
- **Timber must be planted exactly once**, in `BocanApplication.onCreate`. Tests construct trees directly instead of planting.
- **Version catalog drift**: every dependency version lives in `libs.versions.toml`; a hardcoded version string in any `build.gradle.kts` is a review-blocking defect.

## Handoff

Phase 01 assumes: the module skeleton compiles, `AppLog` is available from every module, CI and hooks are green, and `AppGraph` exists as the single wiring point it will extend.
