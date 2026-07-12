# F-Droid readiness

Bòcan Music is a good F-Droid citizen by design: no analytics, no telemetry, no crash
reporting, no ad or tracking libraries, no Google Play Services dependency, and no
proprietary SDKs. The sync connection is the only network traffic, and it stays on the
local network under the user's own pinned certificates. The one thing standing between the
current build and a reproducible F-Droid build is a single native artifact.

## The one blocker: the FFmpeg audio decoder

The app plays formats beyond what the Android platform decoders cover (notably the wider
lossless and legacy codec range) through the Media3 FFmpeg extension. We consume it as a
prebuilt Maven artifact:

```
org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1   (gradle/libs.versions.toml)
```

This ships a precompiled `libffmpegJNI.so` per ABI. F-Droid's inclusion policy requires
that every binary in the shipped app be built from source by the F-Droid build server, and
its reproducible-builds track requires that we produce a byte-identical APK ourselves. A
prebuilt `.so` fails both. This is the same native decoder the Mac app bundles, and it is
what gives the two apps format parity.

## Decision

**Committed path: build the Media3 FFmpeg extension from source in CI, and ship one build
everywhere.** Reproducibility is worth having regardless of F-Droid (it is what lets anyone
verify the GitHub release APK matches the tag), and building the decoder ourselves removes
the only non-free-tooling artifact from the graph, so the same build satisfies F-Droid, the
GitHub release, and Play. We do not split into flavors.

The concrete recipe, to land as a follow-up build job (tracked here, not yet wired, because
it needs the NDK toolchain provisioned and a from-source `.so` validated on a device before
it can replace the prebuilt artifact):

1. Add an NDK build step that clones the Media3 release matching our pinned `media3`
   version, runs its `libraries/decoder_ffmpeg` build script against a from-source FFmpeg
   configured with exactly the decoders we enable (keep the enabled-codec list in the
   script under version control so the build is deterministic and auditable).
2. Package the resulting per-ABI `libffmpegJNI.so` into the app instead of the
   `org.jellyfin.media3` artifact, gated behind a Gradle property so the prebuilt path
   stays available for fast local iteration.
3. Keep the FFmpeg source revision and the Media3 version pinned together in
   `libs.versions.toml`, exactly as the prebuilt pair is pinned today (a mismatch is a
   guaranteed runtime `LinkageError`).
4. Re-run `scripts/check-so-alignment.sh` on the from-source `.so`: F-Droid targets recent
   Android, so the 16 KB page-size alignment (`-Wl,-z,max-page-size=16384`) must hold, the
   same gate the release workflow already enforces on the packaged APK.

## Fallback path (recorded, not chosen)

If the from-source build proves too costly to maintain, ship an `fdroid` product flavor
that omits the FFmpeg extension. It would play only what the Android platform decoders
handle (MP3, AAC, FLAC, Vorbis, Opus, WAV, and the device's other native formats) and must
say so plainly in its F-Droid description and refuse unsupported files with a clear
message rather than failing silently. This is a real regression in format support, which is
why it is the fallback and not the plan.

## Reproducibility posture (already true today)

- JVM toolchain pinned to 21 in every module, so the build does not depend on the machine
  `JAVA_HOME`.
- All dependency versions pinned in `gradle/libs.versions.toml`; no dynamic or `+` versions
  except the FFmpeg artifact's build-metadata suffix, which the from-source path removes.
- No `Date`/time or random inputs baked into the build output.
- Signing is reproducible-build friendly: F-Droid signs with its own key, and the GitHub
  release is signed with our upload key; the unsigned APK bytes are what get compared.

## F-Droid metadata (sketch for the eventual merge request to fdroiddata)

```yaml
Categories:
  - Multimedia
License: <SPDX id from LICENSE>
SourceCode: https://github.com/<owner>/bocan-music-android
IssueTracker: https://github.com/<owner>/bocan-music-android/issues
AutoUpdateMode: Version v%v
UpdateCheckMode: Tags ^v[0-9.]+$
Builds:
  - versionName: <from app/build.gradle.kts>
    versionCode: <versionCodeOf(versionName)>
    commit: v<versionName>
    subdir: app
    gradle:
      - yes
    # ndk + the from-source FFmpeg build steps go here once the recipe above is wired.
```
