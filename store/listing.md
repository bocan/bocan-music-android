# Google Play Console setup (Bòcan Music)

This file is the runbook for the Play Console **"Set up your app"** phase. It is
organised to match the Console's own task list, so each heading below maps to one
task you tick off. Copy for the Store Listing itself lives in the last section.

- **Package name:** `io.cloudcauldron.bocan.android` (the release `applicationId`,
  ASCII, fixed forever once the first bundle is uploaded; the `.debug` suffix is a
  dev-only artifact and never appears in the Console).
- **Brand:** "Bòcan Music" with the grave accent (correct Scottish Gaelic), matching
  the on-device name and the Mac app. The plain-ASCII "Bocan" is woven into the
  descriptions so accent-free searches still match.
- **Default language:** en-GB (in-app copy uses British spelling, e.g. "levelling").

Do not use em dashes or en dashes anywhere in this copy.

---

# Phase 1: Set up your app

## Let us know about the content of your app

### Set privacy policy

- **Privacy policy URL:** TODO (required by Play). Point it at the privacy page on
  the Bòcan website, the same URL used in the app's About screen.
- The page must state plainly what `store/data-safety.md` says: the app collects and
  shares nothing; the only data leaving the phone goes to the user's own paired Mac
  on the local network, or, if the user opts in, to a scrobble service they chose.

### App access (sign-in details)

- Select **"All or some functionality in my app is restricted."** The form's own
  definition of restricted access includes "actions to be carried out on another
  device", and Bòcan's core functionality is exactly that: sync requires pairing with
  a Mac the reviewer does not have, confirmed on the Mac. There is still no account,
  login, or credential anywhere in the app; the restriction is the companion device.
- Add **one** instruction set:
  - **Instruction name:** `Mac companion pairing`
  - **Username / password:** `none` in each (the fields are mandatory but do not apply;
    the free text explains).
  - **"Any other information required to access your app"** (limit 500 characters;
    the copy below is 432 with a `youtu.be`-length link):

  ```
  No accounts, sign-in, passwords, or OTPs exist. Sync requires the user's own Mac running the Bòcan desktop app on the same Wi-Fi; pairing is confirmed on the Mac (mutual TLS, device to device, no server to log into). Without a paired Mac the app launches and all screens are reachable but the library is empty. Demo videos of pairing, sync, and playback: https://youtu.be/XXXXXXXXXXX. Username/password are placeholders; none exist.
  ```

- Use **one** video link in that field: a single combined demo (pairing, then Sync Now,
  then locked-screen playback) or an unlisted YouTube playlist. The two
  foreground-service declarations have their own video fields (see below), so they do
  not need to be squeezed in here. If the URL runs long (e.g. a Drive link), drop the
  last sentence of the copy first.
- Record a short pairing demo alongside the two foreground-service videos so the
  "action on another device" part is covered end to end.

### Ads

- Select **"No, my app does not contain ads."** There is no ads code and no ad SDK.

### Content rating

- Complete the questionnaire as a **music/media player**. Honest answers:
  - No violence, sexual content, profanity, or gambling produced by the app.
  - No user-generated content that is shared or made public through the app.
  - The app plays the user's own local music files; it does not surface a public catalogue.
- Expected result: **Everyone / PEGI 3**. Re-run the questionnaire if features change.

### Target audience and content

- **Target age groups:** adult groups only (e.g. 18+). Do **not** select any child age
  band. This is a general-purpose utility, not directed at children.
- Answer **"No"** to "Is your app designed for children?" so it stays out of the
  Families programme and Designed for Families requirements.
- No appeal to children in the store listing, icon, or screenshots.

### Data safety

- Source of truth: **`store/data-safety.md`**. Fill the form to match it exactly.
- **Collects or shares any required user data type?** No.
- **All data encrypted in transit?** Yes (all traffic is TLS; cleartext is disabled
  app-wide).
- **Way to request data deletion?** Not applicable, we hold no user data. Unpairing and
  "remove media" wipe all local state; scrobble history is managed on the third-party
  service.
- **The one nuance:** optional, opt-in scrobbling sends listening history (track,
  artist, album, timestamp) directly to the service the user chose (Last.fm,
  ListenBrainz, Rocksky). Declared as **not collected by us** because it goes device to
  third party with the user's own credentials; we never receive or proxy it. Off by
  default. See `store/data-safety.md` for the reviewer rationale and the data-type table.

### Government apps

- Select **"No, this is not a government app."** Bòcan is an independent consumer app.

### Financial features

- Select **"My app doesn't provide any financial features."** No payments, wallet,
  banking, crypto, lending, or trading.

### Health

- Select **"My app doesn't have any health features."** No health, fitness, or medical
  data of any kind.

---

## Manage how your app is organised and presented

### Select an app category and provide contact details

- **App category:** Music & Audio.
- **Tags (choose in Console):** music player, local sync, offline music, podcasts,
  private, no cloud, Mac companion, FLAC, equalizer.
- **Contact email:** TODO (shown publicly on the listing).
- **Contact website:** TODO (the "Bòcan on the web" URL).
- **Contact phone:** optional; leave blank unless you want it public.
- **Developer / contact name:** TODO (your Play Developer account name, keep ASCII).

### Set up your Store Listing

Character limits are Google's; counts below are current.

#### App title

Limit: 30 characters. Current: 11.

```
Bòcan Music
```

#### Short description

Limit: 80 characters. Current: 76.

```
Bòcan (Bocan): your Mac's music library on your phone. No cloud, no account.
```

#### Full description

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

#### Graphics

- App icon, feature graphic, and phone screenshots in both light and dark themes are
  produced in phase 12. This file is copy only.
- Before publishing, search Play for existing "Bocan" / "Bòcan" apps to rule out a
  confusing name collision. Google Play permits diacritics in real words; the accent is
  fine in the title.

---

## Foreground service declarations (needed for the review)

The app declares two foreground service types; each needs a demo video recorded to
`store/` (see `store/permissions.md` for the full rationale):

- **`mediaPlayback`:** demo = open the library, tap a track, lock the phone, show audio
  continuing with the notification controls.
- **`dataSync`:** demo = Settings, then Sync Now, with the sync-progress notification
  advancing while the app is backgrounded.

Neither service runs while idle, so no foreground service is held at rest.
