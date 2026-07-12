# Phase 12: Release Engineering

> Depends on: everything before it, plus phase 13 (gestures land before release)
> Read docs/design-spec/_standards.md first. The Mac reference for pipeline philosophy is `bocan-music/docs/design-spec/phase-16-distribution.md` and the repo's release-please setup.
> Provides: reproducible signed builds, CI/CD, versioning, store readiness.

## Goal

One comprehensible pipeline: every push runs checks; a release is a tagged, signed, reproducible build with a changelog derived from Conventional Commits, published as a GitHub release APK, with Play Store and F-Droid tracks prepared.

## Non-goals

- No paid store presence decisions (pricing, listings copy beyond drafts).
- No crash-reporting SaaS; the no-telemetry stance holds.

## Implementation plan

1. **Build types and signing**: `debug` (suffix `.debug`, debuggable) and `release` (minified with R8, resource shrinking, proguard rules for Media3/Room/kotlinx-serialization verified by a smoke run of every major flow on the release build). Release signing via a keystore in GitHub Actions secrets (base64), never in the repo; local release builds use `local.properties` paths.
2. **Versioning**: release-please (or conventional-changelog equivalent) maintaining `versionName` from Conventional Commits; `versionCode` derived monotonically from the version (major*10000 + minor*100 + patch). The `fix(scope):`/`feat(scope):` discipline from `_standards.md` feeds this.
3. **CI matrix** (`ci.yml` extended): lint + detekt + unit tests + Kover verify on every push/PR; assembleRelease (unsigned) on PRs to catch R8 breaks early; an instrumented smoke job on an emulator (API 29 and latest: launch, browse fixture DB, play a bundled test asset) nightly rather than per-push to keep PRs fast.
4. **Release workflow** (`release.yml`): on release-please tag: build signed APK + AAB, attach APK to the GitHub release with checksums, upload AAB to Play internal track via the Gradle Play Publisher when Play is configured.
5. **F-Droid readiness**: document (in `docs/fdroid.md`) the reproducibility posture and the one blocker to resolve: the prebuilt FFmpeg decoder artifact. Options: build the FFmpeg extension from source in CI (preferred for F-Droid, needed anyway for reproducibility) or ship an F-Droid flavor without it (reduced format support, clearly labelled). Decide and record; if building from source, add the NDK build job here.
6. **Privacy and store metadata**: data-safety declaration (no data collected; optional scrobble providers send listening data to the user's chosen service; sync stays on-LAN), permissions rationale (network, foreground service dataSync + mediaPlayback, POST_NOTIFICATIONS), store listing draft in `store/` with screenshots generated from the app in both themes. Play Console foreground-service declarations for BOTH types (`mediaPlayback`, `dataSync`), each with the required demo video (playback: play a track from the library; dataSync: Settings -> Sync Now with the progress notification visible); record the videos as part of this phase and keep them in `store/`.
7. **16 KB page-size compliance gate**: a CI step on release artifacts verifying every packaged `.so` is 16 KB aligned (apkanalyzer or the alignment script from the Android docs), failing the build otherwise. This is a Play hard requirement for apps targeting Android 15+ and the FFmpeg decoder is the only native code that can break it.
8. **Release checklist** (`docs/release-checklist.md`): the phase 10 manual matrix (Auto DHU, Bluetooth, widget), a pairing + full-sync run against a real Mac, upgrade-in-place test from the previous release (database and DataStore migrations exercised), the R8 smoke, Play Console declarations up to date (foreground services, data safety), and the 16 KB alignment gate green.

## Context7 lookups

- use context7: Gradle Play Publisher setup service account
- use context7: release-please for non-Node projects Android versioning
- use context7: R8 keep rules for kotlinx-serialization and Room

## Dependencies

release-please action, Gradle Play Publisher (when Play configured).

## Test plan

- CI green on a synthetic release PR end to end (tag on a fork/branch, artifacts produced, checksums valid).
- Install the signed release APK on a device: full flow (pair, sync, play, podcast, scrobble, Auto) with R8 enabled.
- Upgrade test: install previous release, add data, install new build over it, everything survives.

## Acceptance criteria

- [ ] Push-to-release requires no manual steps beyond merging the release PR.
- [ ] Signed release APK on the GitHub release page with checksums; version and changelog correct.
- [ ] R8 release build passes the full manual smoke.
- [ ] Upgrade-in-place proven.
- [ ] Data-safety and permissions docs committed; store listing draft present.
- [ ] F-Droid decision recorded with a working path.

## Gotchas

- **R8 breaks are runtime breaks.** kotlinx-serialization and Room reflectionless paths still need keep rules; the PR-time assembleRelease job exists to catch this before release day.
- **`versionCode` can never decrease.** The derivation formula must be tested with a unit test so a 2.0.0 release does not collide with a 1.99.x hotfix scheme.
- **The signing keystore is unrecoverable if lost**; document the backup location (outside git) in the checklist.

## Handoff

The repo is shippable. Future feature phases append to this spec directory following the same template.
