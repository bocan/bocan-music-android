# Google Play listing (draft)

Draft copy for the Play Console listing. Character limits are Google's; counts
below are current. The brand is "Bòcan Music" (with the grave accent, the correct
Scottish Gaelic spelling), matching the on-device app name and the Mac app. The
plain-ASCII form "Bocan" is woven into the short and full descriptions so searches
typed without the accent still match, since not every keyboard produces "ò".

Do not use em dashes or en dashes anywhere in this copy.

## App title

Limit: 30 characters. Current: 11.

```
Bòcan Music
```

## Short description

Limit: 80 characters. Current: 76.

```
Bòcan (Bocan): your Mac's music library on your phone. No cloud, no account.
```

## Full description

Limit: 4000 characters.

```
Bòcan Music (also written "Bocan") is the companion player for the Bòcan music app on your Mac. Pair once, and your music, playlists, and podcasts follow you onto your phone over your own Wi-Fi. No cloud, no account, nothing to sign up for.

Bòcan syncs one way, from your Mac to your phone, with both devices verifying each other's certificates. Your library stays yours: there is no cloud service in the middle, no analytics, and no telemetry. The only things that ever leave your phone are requests to your paired Mac and, if you turn scrobbling on, the plays you send to a service you choose.

WHAT YOU GET

- Your whole library, offline: albums, artists, songs, playlists, and genres, synced to the phone and playable without a connection.
- Real playback quality: gapless playback, ReplayGain volume levelling, a ten-band equalizer with bass boost, and lossless formats including FLAC, APE, and WavPack via a bundled decoder.
- Podcasts: subscribe on the Mac, listen on the phone, with chapters, variable speed, and continue-listening that remembers where you left off.
- Synced lyrics that scroll in time, with tap-to-seek.
- A full Now Playing screen with finger gestures (swipe to change tracks, swipe up for song details, swipe down to dismiss) and a live audio visualiser.
- Sleep timer, playback speed control, and a home-screen widget.
- Android Auto and Bluetooth controls, with lock-screen artwork.
- Optional scrobbling to Last.fm, ListenBrainz, or Rocksky, off by default and only if you turn it on.

PRIVATE BY DESIGN

Bòcan is a player, not an editor: it never changes your files, tags, or playlists. Sync happens over your local network with pinned mutual TLS, so your library never touches a third party. No account. No cloud. No tracking.

WHAT YOU NEED

You need a Mac running the Bòcan music app on the same Wi-Fi network to pair and sync. Bòcan Music is a companion to that app, not a standalone streaming service. Android 10 or newer.
```

## Store metadata

- Application ID: `io.cloudcauldron.bocan.android` (ASCII, fixed)
- Category: Music & Audio
- Tags / keywords to select in Console: music player, local sync, offline music,
  podcasts, private, no cloud, Mac companion, FLAC, equalizer
- Developer / contact name: TODO (your Play Developer account name, keep ASCII)
- Website: TODO (the "Bòcan on the web" URL used in the About screen)
- Privacy policy URL: TODO (required by Play; can point at the website's privacy page)
- Default language: en-GB (the in-app copy uses British spelling, e.g. "levelling")

## Data safety (Play Console declaration)

Fill the Data safety form to match the app's actual behaviour:

- Data collected: none by the app itself.
- Data shared: only if the user opts in to scrobbling, the app sends listening
  history (track, artist, timestamp) to the third-party service the user chooses
  (Last.fm, ListenBrainz, or Rocksky). Off by default.
- Sync traffic stays on the local network between the phone and the paired Mac; it
  does not leave the user's network.
- No analytics, no advertising, no tracking, no third-party SDKs that collect data.

## Foreground services (Play Console declaration)

The app declares two foreground service types; each needs its demo video in
`store/` per phase 12:

- `mediaPlayback`: demo = play a track from the library.
- `dataSync`: demo = Settings, then Sync Now, with the progress notification visible.

## Notes on the accent

- Keep the title exactly "Bòcan Music". Google Play permits diacritics in titles;
  the policy against "special characters" targets gimmicks (emoji, symbols, unicode
  tricks), not a legitimate accented letter in a real word.
- Before publishing, search Play for existing "Bocan" / "Bòcan" apps to rule out a
  confusing name collision.
- Assets (icon, feature graphic, screenshots in both themes) are produced in phase
  12; this file is copy only.
