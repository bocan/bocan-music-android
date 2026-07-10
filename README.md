# Bòcan Music for Android

A native Android companion app for [Bòcan Music](https://github.com/bocan/bocan-music), the macOS music player for people who still own their music.

Bòcan for Android does two things:

1. **Syncs your library from your Mac to your phone, one way.** The Mac is the source of truth. Pair once with a code shown on the Mac's screen; after that, whenever the phone and the Mac see each other on the same network, the phone pulls down whatever changed. No cloud, no account, no subscription, no telemetry.
2. **Plays it, properly.** Gapless playback, ReplayGain, a 10-band EQ, synced lyrics, podcasts with per-episode resume and chapters, scrobbling, Android Auto, a home-screen widget, and Material You theming.

It is a music player first and foremost. It never edits your files, your tags, or your playlists. All of that happens on the Mac; the phone receives and plays.

## Status

Pre-implementation. This repository currently holds the complete design specification under `docs/design-spec/`, written so that the app can be built phase by phase, each phase in a fresh session, by any capable coding model. Start with `docs/design-spec/README.md`.

## Stack (decided)

| Concern | Choice | Why |
|---------|--------|-----|
| Language | Kotlin 2.x, JDK 17+ | The native Android language; Swift-like ergonomics keep the two codebases culturally similar |
| UI | Jetpack Compose, Material 3 | Declarative UI to mirror the Mac app's SwiftUI approach; Material You dynamic color |
| Playback | AndroidX Media3 1.10+ (ExoPlayer, MediaSession) plus the Media3 FFmpeg decoder extension | Gapless, speed/pitch, skip-silence out of the box; FFmpeg covers APE, WavPack, DSD, Musepack and friends, mirroring the Mac's AVFoundation/FFmpeg split |
| Database | Room (SQLite) with FTS | The Android analogue of the Mac app's GRDB layer; reactive Flows mirror ValueObservation |
| Discovery | `NsdManager` (mDNS / Bonjour) | The Mac advertises `_bocansync._tcp`; both platforms speak Bonjour natively |
| Transport | HTTPS over mutual TLS with pinned self-signed certs, OkHttp client | Pairing binds the two device certificates; everything after is pinned both ways |
| Background sync | WorkManager plus a foreground service for active transfers | Survives Doze; visible progress notification during large pulls |
| Serialization | kotlinx.serialization | Manifest and protocol JSON |
| Images | Coil 3 | Artwork loading from the synced cache |
| DI | Manual constructor injection via a single `AppGraph` | No Hilt/KSP build fragility; the object graph is small enough to read |

## Architecture at a glance

Strict module DAG, mirroring the Mac app's layering (no upward imports):

```
:core:observability  ->  :core:persistence  ->  :core:sync, :core:playback, :core:scrobble  ->  :app
```

- `:core:observability` owns the `AppLog` facade (Timber-backed) and redaction.
- `:core:persistence` owns the Room schema, DAOs, and reactive queries.
- `:core:sync` owns discovery, pairing, the trust store, and the manifest-diff sync engine.
- `:core:playback` owns the Media3 player, `MediaLibraryService`, gapless queue, ReplayGain, EQ.
- `:core:scrobble` owns the Last.fm / ListenBrainz / Rocksky providers and offline queue.
- `:app` owns every Compose screen, navigation, widgets, Android Auto surface, and settings.

## The sync model in one paragraph

The Mac runs a small "Phone Sync" server (a new module in the bocan-music repo, specified in `docs/design-spec/phase-mac-1-sync-server.md`). It advertises itself over Bonjour and serves a signed-in-TLS, versioned JSON **manifest**: every track (with full metadata, ReplayGain values, ratings, lyrics hashes), every playlist materialized to an ordered track list (smart playlists are evaluated on the Mac), every podcast show and downloaded episode, and every piece of artwork by content hash. The phone diffs the manifest against its Room database, downloads new or changed files with resume support, verifies each by SHA-256, deletes files that left the sync set, and applies the metadata in one transaction. Sync is strictly Mac to phone. Phone-local state (play counts, episode positions after first seed, scrobble queue) lives in separate tables that sync never touches.

## Icons

`assets/icons/` contains the Bòcan brand assets copied from the Mac repo: the layered SVG app icon (`AppIcon.icon/` with moon, spirit, face, and guitar layers over teal/indigo gradients), a 1024 px `favicon.svg`, and PNG renders. Phase 00 turns these into an Android adaptive icon.

## License

Apache 2.0, same as bocan-music.
