# F-Droid: decided against

Bòcan Music is a good F-Droid citizen on paper (no analytics, telemetry, crash reporting,
ad or tracking libraries, Google Play Services, or proprietary SDKs; the only network
traffic is the on-LAN sync under the user's own pinned certificates). Despite that, we have
decided **not** to distribute on F-Droid. This document records that decision so it reads as
a deliberate choice, not an oversight.

## Why not

1. **The FFmpeg prebuilt is a hard blocker.** The app plays formats beyond the Android
   platform decoders through the Media3 FFmpeg extension, consumed as a prebuilt Maven
   artifact (`org.jellyfin.media3:media3-ffmpeg-decoder`, see `gradle/libs.versions.toml`),
   which ships a precompiled `libffmpegJNI.so` per ABI. F-Droid rebuilds everything from
   source and its scanner rejects prebuilt binaries, so getting on F-Droid would require
   either building the FFmpeg extension from source in the recipe (an NDK toolchain plus a
   from-source `.so` validated per device, then kept pinned to the Media3 version forever) or
   shipping a cut-down flavor without the decoder (a real regression in format support). Both
   are ongoing maintenance for a channel a small slice of users would use.
2. **The submission path runs through GitLab.** F-Droid inclusion means merge requests to
   `fdroiddata` on GitLab, which is not a friendly platform for open-source contribution
   workflows: the round-trip of forking, staging, MR, and maintainer review is heavy for a
   personal project, and the tooling gets in the way more than it helps.
3. **Developer verification cuts the other way.** Google's developer-verification rollout
   targets sideloading in general, not just Play, so F-Droid would not have been a refuge
   from it. The practical response is to be on Play, which is already the plan.

Weighed against near-zero incremental reach, the cost is not worth it.

## What we distribute instead

- **GitHub Releases.** The signed, versioned, checksummed APK produced by `release.yml` is
  the sideload channel and already works end to end (`bocan-music-<version>.apk`).
- **Google Play.** The main channel once the corporate account is approved; the pipeline is
  wired and secret-gated, ready to publish the AAB.

Between them, skipping F-Droid loses very little.

## Consequence for the FFmpeg pin

The prebuilt `org.jellyfin.media3:media3-ffmpeg-decoder` pin in `libs.versions.toml` is now
**permanent**, not a temporary state pending an F-Droid from-source build. It is fine for
Play and for direct download; only F-Droid ever required otherwise. Keep it pinned in step
with the `media3` version (a mismatch is a guaranteed runtime `LinkageError`); that is the
only ongoing obligation.

## If this is ever reconsidered

The blocker and the two ways through it have not changed: build the Media3
`libraries/decoder_ffmpeg` extension from source in CI (also buys reproducible builds), or
add an `fdroid` product flavor without the decoder. Reproducible-build posture is already
most of the way there (JVM toolchain pinned to 21, all dependency versions pinned, no
time or random inputs in the output). Revisit this file, not the code, as the starting point.
