# Release checklist

How a release is cut, and everything that must be true before one ships. The pipeline is
designed so that cutting a release is one action (merge the release PR); this list is the
human verification around that automation, plus the one-time setup.

## How the pipeline works

1. Every push to a branch and every PR runs `.github/workflows/ci.yml`: ktlint, detekt,
   Android Lint, all unit tests, the buildSrc versionCode test, `koverVerify`, an unsigned
   minified `assembleRelease` (catches R8 breaks), and the 16 KB `.so` alignment gate.
2. On every push to `main`, `.github/workflows/release-please.yml` maintains a release PR.
   It accumulates Conventional Commit messages into `CHANGELOG.md` and bumps the version:
   `feat` bumps the minor, `fix` the patch, and a breaking change bumps the minor while the
   app is pre-1.0 (`bump-minor-pre-major`). It updates `versionName` in `app/build.gradle.kts`
   (the `x-release-please-version` line) and `version.txt`.
3. Merging that release PR tags `vX.Y.Z` and creates the GitHub release with the changelog
   as its notes.
4. That publish event triggers `.github/workflows/release.yml`, which builds the signed APK
   and AAB, re-checks 16 KB alignment on the release artifact, attaches the APK, AAB, and
   `checksums.txt` to the release, and (once configured) uploads the AAB to the Play
   internal track.

`versionCode` is derived from `versionName` by `versionCodeOf` in `buildSrc` and is never
hand-edited. The About screen shows `BuildConfig.VERSION_NAME`, so the in-app version always
matches the tag.

## One-time setup

### Signing keystore (required before the first signed release)

The upload keystore is **unrecoverable if lost**: lose it and you can never update the same
Play listing again. Generate it once, back it up outside git, and record the backup here.

- Generate: `keytool -genkeypair -v -keystore bocan-upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias bocan`
- **Backup location (fill in and keep private):** `__________________________`
  (password manager entry or offline encrypted volume; never in the repo, never in a chat).
- Local release builds read `keystore.properties` at the repo root (git-ignored). Fields:
  `BOCAN_KEYSTORE_FILE`, `BOCAN_KEYSTORE_PASSWORD`, `BOCAN_KEY_ALIAS`, `BOCAN_KEY_PASSWORD`.
- CI reads the same field names from GitHub Actions secrets. Set:
  - `BOCAN_KEYSTORE_BASE64` = `base64 -i bocan-upload.jks` (the release workflow is gated on
    this secret; without it, the signed build is skipped and CI stays green).
  - `BOCAN_KEYSTORE_PASSWORD`, `BOCAN_KEY_ALIAS`, `BOCAN_KEY_PASSWORD`.

### Last.fm keys (optional, hides the Last.fm provider if absent)

The player carries only these two build-time secrets; ListenBrainz and Rocksky are per-user
token providers and need no app key.

- Local: add `BOCAN_LASTFM_API_KEY` and `BOCAN_LASTFM_SHARED_SECRET` to `local.properties`.
- CI: add the same two as GitHub Actions secrets; `release.yml` passes them into the build.
- Missing keys leave the Last.fm provider hidden rather than crashing.

### Play Console (deferred until the corporate account is approved)

- Create the app, then a service account with the Play Android Publisher role, download the
  JSON, and add it as the `ANDROID_PUBLISHER_CREDENTIALS` secret. The Play upload step in
  `release.yml` and `scripts/play-publish.init.gradle.kts` are gated on this secret and do
  nothing until it exists.
- The Gradle Play Publisher path is prepared but **unvalidated**; do the first internal-track
  upload by hand or watch the first automated run closely.
- Complete the store listing from `store/listing.md`, the data-safety form from
  `store/data-safety.md`, and the permissions rationale from `store/permissions.md`.
- Declare **both** foreground service types and attach the demo videos (see below).

### Demo videos for foreground services (record once, keep in `store/`)

- `mediaPlayback`: open the library, play a track, lock the phone, show audio and the
  notification controls continuing.
- `dataSync`: Settings, Sync Now, show the sync-progress notification advancing while the
  app is backgrounded.

## Per-release verification

Run these against the **signed release build** (R8 on), not a debug build.

### Automated gates (must be green)

- [ ] CI green on the release PR: lint, tests, versionCode test, koverVerify, unsigned
      `assembleRelease`, 16 KB alignment.
- [ ] `versionName`, `versionCode`, and `CHANGELOG.md` in the release PR are correct.

### R8 smoke (the whole app, on a device, release build)

R8 breaks are runtime breaks, so exercise every major flow on the actual signed build:

- [ ] Pair with a real Mac and complete a full sync over mutual TLS.
- [ ] Browse the library (10k-track scroll stays smooth), play local files of each major
      format including an FFmpeg-decoded one (proves the FFmpeg keep rules held).
- [ ] Now Playing: transport, seek, queue, the four gestures, EQ and effects.
- [ ] Podcasts: subscribe/continue-listening, artwork, playback.
- [ ] Scrobble: enable a provider and confirm a play is submitted.

### Phase 10 device matrix (manual, not automatable)

- [ ] Android Auto via the Desktop Head Unit (DHU): browse and play.
- [ ] Bluetooth: route to a device, transport controls and metadata correct.
- [ ] Home-screen widget: renders, updates, controls work.

### Upgrade-in-place

- [ ] Install the previous release, add data (pair, sync, scrobble a play, subscribe to a
      podcast), install the new build over it, confirm everything survives: Room and
      DataStore migrations run, pairing and media intact, no re-onboarding.

### 16 KB and Play declarations

- [ ] 16 KB alignment gate green on the release artifact (enforced in `release.yml`).
- [ ] Play Console foreground-service declarations and demo videos current for both types.
- [ ] Data-safety form matches `store/data-safety.md`.

## Outstanding manual QA carried from earlier phases

These are device-verification items that were left unticked in their phase files because
they need real hardware and accounts. They gate the first public release, not the pipeline,
and belong on this list:

- [ ] Phase 09: a real Last.fm account receives a scrobble from a device test (document in
      the release notes or the phase 09 PR). This is the untested integration to prove out;
      set `BOCAN_LASTFM_API_KEY` / `BOCAN_LASTFM_SHARED_SECRET` first.
- [ ] Phase 11: onboarding takes a fresh install to playing music with no detours.
- [ ] Phase 11: an `en-XA` pseudolocale pass shows no truncation or string concatenation
      defects.
- [ ] Phase 11: performance baselines re-verified and recorded (cold launch under 1.5 s,
      60 fps scroll, seek under 100 ms, zero wakelocks while paused).
