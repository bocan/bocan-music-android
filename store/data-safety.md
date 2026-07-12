# Play Data safety declaration

Source of truth for the Play Console "Data safety" form. Bòcan Music collects nothing and
sends nothing to us. The only data that ever leaves the phone goes either to the user's own
paired Mac on the local network or, if the user turns scrobbling on, to a scrobble service
the user chose and authenticated with directly.

## Summary answers

- **Does your app collect or share any of the required user data types?** No.
- **Is all of the user data collected by your app encrypted in transit?** Yes (all network
  traffic is TLS; cleartext is disabled app-wide).
- **Do you provide a way for users to request that their data be deleted?** Not applicable:
  we hold no user data. Scrobble history lives on the third-party service and is managed
  there; unpairing and "remove media" wipe all local state on the device.

## Why "no data collected" is accurate

- No analytics, no telemetry, no crash reporting SDK. This is a binding standard for the
  project, not a toggle (`docs/design-spec/_standards.md`, Security and privacy).
- No account with us; there is no Bòcan account, sign-in, or server.
- The sync connection is device-to-device on the local network, authenticated by pinned
  mutual TLS. Music, artwork, and metadata flow one way from the user's Mac to the phone.
  Nothing is uploaded to us or to any cloud.
- Synced media lives in app-specific external storage and is never modified or exfiltrated.

## The one nuance the form must capture: optional scrobbling

Scrobbling is off by default and opt-in per provider. When a user enables Last.fm,
ListenBrainz, or a ListenBrainz-compatible service (Rocksky), the app sends that user's
music-listening activity (track title, artist, album, timestamp) directly to the service
the user selected, using credentials the user entered. This is:

- **User-initiated and optional**, not collection by us: the data goes to the user's chosen
  service, and we never receive or proxy it.
- Declared on the form as **not collected by us**; if the Play reviewer asks, the rationale
  is that the destination is a third party the user authenticated with directly, analogous
  to the user posting to a social service from within an app. Podcasts never scrobble.

Credentials for those services are stored encrypted on-device (Keystore-wrapped storage),
never in plain preferences and never in the database, and are redacted from all logs.

## Data types, for the reviewer's cross-check

| Play data type | Collected | Shared | Notes |
|----------------|-----------|--------|-------|
| App activity (listening history) | No (by us) | Only to the user's chosen scrobble service, opt-in | Direct device-to-service; we never receive it |
| Files and docs (music) | No | No | Synced from the user's own Mac to app-private storage |
| Personal info, location, contacts, financial, health | No | No | Never touched |
| Device or other IDs | No | No | No advertising ID, no device fingerprinting |
