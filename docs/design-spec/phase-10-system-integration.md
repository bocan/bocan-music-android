# Phase 10: System Integration

> Depends on: phase-04-playback-engine.md, phase-05-library-ui.md, phase-07-podcasts.md
> Read docs/design-spec/_standards.md first.
> Provides: Android Auto, a home-screen widget, notification and Bluetooth polish, and app shortcuts. The "feels like a real Android music app" phase.

## Goal

Bòcan shows up everywhere Android users expect a music app to: a browsable library in Android Auto, a resizable Glance widget, a rich media notification with podcast skip buttons when appropriate, correct AVRCP metadata on car and headphone displays, and long-press app shortcuts.

## Non-goals

- No Wear OS app, no Android TV (future repos or phases).
- No Assistant "play X by Y" voice search in v1 (stub `onSearch` gracefully; full support is a known follow-up).

## Outcome shape

```
core/playback/src/main/kotlin/io/cloudcauldron/bocan/playback/
  browse/MediaTree.kt            // MediaLibraryService browse tree
  session/SessionCommands.kt     // custom commands: skip intervals, shuffle strategy, speed cycle
  session/NotificationCustomizer.kt
app/src/main/kotlin/io/cloudcauldron/bocan/app/widget/
  BocanWidget.kt BocanWidgetReceiver.kt WidgetTheme.kt
app/src/main/res/xml/{shortcuts.xml, automotive_app_desc.xml}
```

## Implementation plan

1. **Media browse tree** (`MediaTree`) for `MediaLibraryService.onGetChildren`:
   root -> [Continue Listening, Playlists, Albums, Artists, Podcasts, Songs (recently synced first)]. Items carry artwork Uris (content-provider-free: use the session's bitmap loader over `ArtworkStore` files). Depth and page sizes tuned for Auto (batches of 50). `onGetLibraryRoot` differentiates Auto clients via the connection hints and can offer a shallower tree.
2. **Auto manifest wiring**: `automotive_app_desc.xml`, the `androidx.car.app` intent filters for media, icon assets. Validate with the Desktop Head Unit (DHU) and document the run steps in the PR.
3. **Custom session commands**: skip-back/skip-forward (episode context), cycle-speed, shuffle toggle carrying strategy. Notification (`MediaNotification.Provider` customizer): music items show previous/play/next; episode items show skip-back/play/skip-forward. Compact view: three actions.
4. **AVRCP/Bluetooth**: verify metadata (title, artist, album, duration, position) lands on a headunit/headphones via the session; fix any missing `MediaMetadata` fields. Handle `KEYCODE_MEDIA_*` including double-tap-next from single-button headsets (the session handles this; test it).
5. **Glance widget**: resizable 4x1 to 4x2: artwork, title/artist, play/pause, next; podcast variant swaps next for skip-forward. Updates driven by the session via a small `WidgetUpdater` observing `PlayerUiState` (foreground) and the media notification's update hook; tapping artwork deep-links into Now Playing (`bocan://nowplaying`).
6. **App shortcuts** (`shortcuts.xml`): Resume playback, Shuffle library, Continue listening (latest in-progress episode), Sync now.
7. **Autoplay etiquette**: `setHandleAudioBecomingNoisy` already pauses on unplug (phase 04); add optional "resume on headphones reconnect" setting default off (implemented via a `BluetoothProfile`/AudioDeviceCallback listener, careful with foreground-start restrictions: only resume if the service is already in the foreground or within the allowed window).

## Definitions and contracts

- Custom commands are namespaced `io.cloudcauldron.bocan.command.<name>` with stable string constants in `SessionCommands`; every external surface (notification, Auto, widget) triggers behaviour only through session commands, never by binding to internals.
- The widget renders from a single `WidgetState` snapshot (parcel-friendly), so Glance recomposition is deterministic and testable.

## Context7 lookups

- use context7: Media3 MediaLibraryService Android Auto browse tree requirements and content style hints
- use context7: Media3 custom commands MediaNotification.Provider customization
- use context7: Glance app widget latest API state updates and deep links
- use context7: Android 13+ foreground service start restrictions media playback exemptions

## Dependencies

Glance. DHU for manual Auto testing.

## Test plan

- `MediaTree`: children of every node against fixture DB (ids, order, page bounds); Auto root vs phone root variants.
- Session commands: each command mutates the player as intended (Robolectric + controller).
- Notification actions: episode vs music action sets chosen correctly (unit-test the provider's action selection as a pure function of the current mediaId type).
- Widget: `WidgetState` mapping from `PlayerUiState`; deep links resolve.
- Manual matrix documented in the PR: DHU browse + play, Bluetooth car display metadata, headset button single/double/triple press, widget on API 29 and latest.

## Acceptance criteria

- [ ] Android Auto (DHU): browse Continue Listening, Playlists, Albums, Artists, Podcasts, Songs; play from each; artwork shows; skip buttons match item type.
- [ ] Media notification: correct actions per item type, artwork, seekable progress on API 33+.
- [ ] Widget: controls work, updates within a second of state changes while the process lives, and survives launcher restart.
- [ ] Bluetooth headunit shows title/artist/album and position; headset buttons work including double-tap next.
- [ ] App shortcuts all function.
- [ ] No wakelock or foreground service lingers after pause + widget/Auto disconnect.

## Gotchas

- **Auto rejects deep trees and slow `onGetChildren`.** Serve from Room with indices, never compute folder trees on demand here; cap depth at 2 from root.
- **Glance is not Compose.** No arbitrary composables; design the widget with Glance primitives only, and test on a cold process (widget must render from persisted state without the app running).
- **Foreground-start restrictions**: a widget play-tap when the service is dead must route through `MediaButtonReceiver`-style session activation (media exemption), not a plain `startForegroundService`.
- **Artwork in notifications**: recycle-safe bitmap loading with explicit sizes; a full-resolution FLAC cover in a notification will ANR low-RAM devices.

## Handoff

Phase 11 polishes copy and accessibility over these surfaces. Phase 12 needs the Auto validation steps documented for release checklists.
