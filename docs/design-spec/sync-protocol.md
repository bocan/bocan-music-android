# Bòcan Sync Protocol v1

> This file is the contract. The Android client (`:core:sync`, phases 02 and 03) and the macOS server (`SyncServer` module in the bocan-music repo, `phase-mac-1-sync-server.md`) both implement exactly this document. Behaviour changes require editing this file first, in both repos if they ever diverge, and bumping `protocolVersion`.

## Overview

One Mac, one or more phones. The Mac is the source of truth and the server. The phone discovers the Mac over mDNS, pairs once using a code shown on the Mac's screen, and thereafter connects over mutual TLS with both certificates pinned. Sync is strictly one way: the phone downloads a manifest describing the Mac's chosen sync set, diffs it locally, pulls files, and never writes anything back. The only phone-to-Mac traffic is HTTPS requests.

Threat model: a home or office LAN. The protocol must make a silent man-in-the-middle during pairing detectable, and make everything after pairing cryptographically pinned. It does not defend against a compromised endpoint.

## 1. Discovery

The Mac advertises a Bonjour service while Phone Sync is enabled:

- Service type: `_bocansync._tcp.`
- Service name: the Mac's computer name (user-visible, may collide; identity comes from the certificate, never the name).
- Port: an ephemeral listening port chosen at startup.
- TXT record keys:
  - `v` = `1` (protocol version)
  - `fp` = lowercase hex SHA-256 fingerprint of the server certificate (full 64 chars)
  - `pm` = `1` if the server is currently in pairing mode, else `0`

The phone browses with `NsdManager` for `_bocansync._tcp`. A discovered service whose `fp` matches a stored trusted server is a known Mac (sync candidate). One whose `fp` is unknown is shown only in the pairing flow, and only when `pm=1`.

## 2. Identity

Each device generates, once, a P-256 (secp256r1) key pair and a self-signed X.509 certificate:

- Subject and issuer CN: `bocan-mac-<8 random hex>` or `bocan-android-<8 random hex>`.
- Validity: 25 years. There is no renewal path in v1; repairing is the recovery story.
- The Android private key lives in the Android Keystore, non-exportable. The Mac private key lives in the login Keychain.
- The **fingerprint** of a device is the lowercase hex SHA-256 of the certificate's DER encoding. Written `fpMac` and `fpPhone` below.

## 3. TLS

- TLS 1.3 minimum (1.2 acceptable only if the platform stack cannot be forced to 1.3; log a warning).
- The server always presents its certificate and always requests a client certificate.
- **Before pairing completes**: the phone connects with a trust manager that accepts the server certificate without validation but records it; the server accepts any client certificate but records it. All pre-pairing requests are refused except the three `/v1/pair/*` endpoints and `/v1/ping`.
- **After pairing**: the phone refuses any server certificate whose SHA-256 does not equal the pinned `fpMac`. The server refuses any client certificate whose SHA-256 is not in its trusted-devices list. No hostname verification, no CA chains, no system trust store on either side: the pin is the whole trust decision.

## 4. Pairing ceremony

Design: the six-digit code is **derived from both certificate fingerprints**, so it is a verification code, not a secret. If a man-in-the-middle terminates TLS in the middle, the Mac and the phone hold different certificate pairs, compute different codes, and the code the user copies from the Mac's screen fails verification on the phone. Brute-forcing is irrelevant because the code contains no secret entropy; matching is the proof.

Sequence:

1. User clicks "Pair a phone" in the Mac's settings. The Mac enters pairing mode for 120 seconds (`pm=1` in TXT) and shows a waiting sheet.
2. Phone (pairing screen) lists discovered `pm=1` services. User taps the Mac. Phone opens TLS (accept-and-record mode) and calls:

   `POST /v1/pair/start`
   ```json
   { "protocolVersion": 1, "deviceName": "Chris's Pixel", "noncePhone": "<32 bytes, base64>" }
   ```
   Response `200`:
   ```json
   { "protocolVersion": 1, "serverName": "Chris's MacBook", "nonceMac": "<32 bytes, base64>", "sessionId": "<uuid>" }
   ```
   Each side takes the peer certificate from the TLS layer itself (never from the JSON) and computes its fingerprint.

3. Both sides compute the code:

   ```
   fpLo  = min(fpMac, fpPhone)            lexicographic on the hex strings
   fpHi  = max(fpMac, fpPhone)
   key   = noncePhone || nonceMac          raw bytes, phone nonce first
   msg   = "bocan-pair-v1" || fpLo || fpHi  ASCII bytes of the literal and hex strings
   code  = decimal(first 8 bytes of HMAC-SHA256(key, msg) as unsigned big-endian) mod 1_000_000
   ```
   Rendered as six digits, zero-padded, displayed as `123 456`.

4. The Mac displays the code. The user types it into the phone. The phone compares against its own computation. On mismatch the phone aborts loudly ("This code does not match. Someone may be interfering with your network, or you tapped the wrong Mac.") and discards the recorded certificate.

5. On match the phone calls:

   `POST /v1/pair/confirm`
   ```json
   { "sessionId": "<uuid>", "proof": "<base64 HMAC-SHA256(key = code as ASCII, msg = sessionId as ASCII)>" }
   ```
   The Mac verifies the proof (it knows the code), then shows a final sheet: "Pair with 'Chris's Pixel'? Only accept if the phone shows Paired." with the phone's fingerprint's last 8 hex chars. The user clicks Trust. The Mac persists `{fpPhone, certDER, deviceName, pairedAt}` in its trusted-devices store and responds:
   ```json
   { "status": "paired", "serverId": "<uuid, stable per Mac>" }
   ```
   The phone persists `{serverId, serverName, fpMac, certDER, pairedAt}`.

6. Pairing mode ends. Rate limiting: 3 failed `confirm` proofs or 120 s elapsed cancels the session; new attempts need a fresh "Pair a phone" click, which regenerates nonces.

The final human click on the Mac (step 5) is deliberate: a MITM that fooled only one side produces a visible asymmetry (Mac says paired, phone says failed), and the instruction on the Mac's sheet tells the user what to check. Do not remove it.

Unpairing: either side can forget the other. The Mac's settings lists trusted devices with a Revoke button; revoked fingerprints are refused at the TLS layer immediately.

## 5. HTTP conventions

- HTTP/1.1 over TLS. Header `Content-Length` is required on bodies; chunked transfer encoding is not used in either direction (both ends are ours; this keeps the Mac's hand-rolled server simple and safe).
- All JSON is UTF-8, `Content-Type: application/json`.
- Errors: status code plus body `{ "error": "<machineCode>", "message": "<human text>" }`. Machine codes: `notPaired`, `pairingExpired`, `badProof`, `rateLimited`, `notFound`, `busy`, `internal`.
- The server may respond `503 busy` with `Retry-After` seconds if a library scan is mid-flight and the manifest would be torn.
- Authentication is entirely the mutual-TLS layer; there are no tokens or cookies.

## 6. Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/v1/ping` | any TLS | Liveness + `{protocolVersion, serverId, generation}` |
| POST | `/v1/pair/start` | pre-pairing | Ceremony step 2 |
| POST | `/v1/pair/confirm` | pre-pairing | Ceremony step 5 |
| GET | `/v1/manifest` | paired | The full sync manifest (section 7) |
| GET | `/v1/file/track/{trackId}` | paired | Track audio bytes |
| GET | `/v1/file/episode/{episodeId}` | paired | Podcast episode audio bytes |
| GET | `/v1/artwork/{hash}` | paired | Artwork bytes by content hash |
| GET | `/v1/lyrics/{trackId}` | paired | Lyrics document (section 8) |
| GET | `/v1/chapters/{episodeId}` | paired | Podcasting 2.0 chapters JSON, as cached by the Mac |

File endpoints:

- Support `Range: bytes=start-` (single open-ended range is sufficient) and reply `206` with `Content-Range`. This is the resume mechanism.
- Set `ETag` to the item's `sha256` from the manifest. The client sends `If-Match` with the expected hash; if the file changed since the manifest was fetched the server replies `412` and the client re-fetches the manifest.
- `Content-Type` is best-effort (`audio/flac`, `audio/mpeg`, `application/octet-stream` fallback); the client never trusts it for format detection.

## 7. Manifest

`GET /v1/manifest` returns the entire sync set. It is a snapshot: the server builds it from a single database read so it is internally consistent. `generation` is a monotonically increasing integer bumped whenever the sync set's content changes (library edit, playlist change, sync-profile change, new podcast download). The client compares `generation` from `/v1/ping` against its last applied value to decide whether a sync is needed.

Size guidance: at 10,000 tracks the manifest is a few MB of JSON. Serve it gzip-encoded when the client sends `Accept-Encoding: gzip`. Do not paginate in v1.

```json
{
  "protocolVersion": 1,
  "serverId": "<uuid>",
  "serverName": "Chris's MacBook",
  "generation": 42,
  "generatedAt": "2026-07-10T12:00:00Z",
  "tracks": [ Track ],
  "playlists": [ Playlist ],
  "podcasts": [ Podcast ],
  "episodes": [ Episode ]
}
```

### Track

```json
{
  "id": 123,
  "relPath": "Artist/Album/01 Title.flac",
  "size": 31337000,
  "sha256": "<hex>",
  "format": "flac",
  "durationMs": 254000,
  "title": "Title",
  "artist": "Artist",           "artistId": 7,
  "albumArtist": "Artist",      "albumArtistId": 7,
  "album": "Album",             "albumId": 55,
  "trackNumber": 1,   "trackTotal": 12,
  "discNumber": 1,    "discTotal": 1,
  "year": 1994,
  "genre": "Shoegaze",
  "composer": null,
  "bpm": null,
  "rating": 80,
  "loved": true,
  "sampleRate": 44100, "bitDepth": 16, "bitrate": 987, "channelCount": 2, "isLossless": true,
  "replayGain": { "trackGain": -8.1, "trackPeak": 0.98, "albumGain": -7.9, "albumPeak": 0.99 },
  "artworkHash": "<hex or null>",
  "lyricsHash": "<hex or null>",
  "clip": null
}
```

Notes:

- `id` is the Mac's stable track id. The phone uses it as its primary key. Ids are stable across manifests; a re-added file gets a new id.
- `relPath` is a sanitized relative path (no leading `/`, no `..`, forward slashes, NFC-normalized). The phone stores the file at `<mediaRoot>/library/<relPath>` but treats `relPath` as opaque; identity is `id`, change detection is `sha256`.
- `rating` is 0 to 100, matching the Mac's schema. `loved` is the favourite flag. Both are display-only on the phone.
- CUE virtual tracks: `clip` is `{ "sourceTrackId": 122, "startMs": 0, "endMs": 254000 }`. The audio bytes belong to the source track's file; a clipped track has no file of its own (its `relPath`, `size`, `sha256` duplicate the source's, and the client must only download the source once).

### Playlist

```json
{
  "id": 9,
  "name": "Late Night",
  "kind": "manual",
  "parentId": null,
  "sortOrder": 3,
  "accentColor": "#A259FF",
  "artworkHash": null,
  "trackIds": [123, 456, 789]
}
```

`kind` is `manual`, `smart`, or `folder`. Smart playlists are evaluated on the Mac at manifest-build time and arrive as plain ordered id lists; the phone never sees the rules. Folders have empty `trackIds` and exist for hierarchy. `trackIds` may reference only tracks present in this manifest (server guarantees it).

### Podcast and Episode

```json
{
  "id": 4,
  "title": "Some Show",
  "author": "Someone",
  "descriptionHtml": "<p>...</p>",
  "artworkHash": "<hex>",
  "playbackSpeed": 1.2
}
```

```json
{
  "id": "sha256-of-guid-truncated-32",
  "podcastId": 4,
  "guid": "<original guid>",
  "title": "Episode 12",
  "publishedAt": "2026-06-01T09:00:00Z",
  "durationMs": 3600000,
  "descriptionHtml": "<p>show notes</p>",
  "relPath": "Podcasts/4/abcd1234....mp3",
  "size": 55000000,
  "sha256": "<hex>",
  "hasChapters": true,
  "playPositionMs": 1200000,
  "playState": "inProgress"
}
```

Only episodes **downloaded on the Mac** and inside the sync profile appear. `playPositionMs` / `playState` are the Mac's values at manifest time; the phone uses them only to seed episodes it has never played (see phase 01's local-state rules). `playbackSpeed` on the show is the Mac's per-show override, used as the phone's initial default for that show.

## 8. Lyrics document

`GET /v1/lyrics/{trackId}` returns:

```json
{ "trackId": 123, "kind": "synced", "text": "[00:12.00]First line\n[00:15.30]Second line" }
```

`kind` is `synced` (LRC body) or `unsynced` (plain text). The client caches by the manifest's `lyricsHash` and refetches when it changes.

## 9. Client sync algorithm (normative)

1. `GET /v1/ping`. If `generation == lastAppliedGeneration`, stop.
2. `GET /v1/manifest`. Validate `protocolVersion`, `serverId`.
3. Diff against the local DB by id:
   - tracks/episodes present locally but absent from the manifest: mark for deletion.
   - present in both but `sha256` differs: mark for re-download.
   - absent locally: mark for download.
   - metadata-only changes (same `sha256`, different tags/rating/playlists): DB update only, no transfer.
4. Download queue: artwork first (small, unblocks UI), then tracks, then episodes. Each file: request with `If-Match` and `Range` if a `.part` exists; stream to `<target>.part`; on completion verify SHA-256; atomic rename into place. Hash mismatch deletes the `.part` and retries once, then records a per-item failure without failing the sync.
5. Apply the manifest to Room in one transaction: upsert synced tables, delete departed rows, seed local-state rows for never-seen items. Set `lastAppliedGeneration`.
6. Delete files marked in step 3 only after the transaction commits, then remove empty directories.

Interrupted syncs are safe by construction: files land atomically, the DB flips in one transaction, and a re-run converges. The client must tolerate the server disappearing mid-transfer (Mac lid closed) by pausing the queue and resuming on next discovery.

## 10. Versioning

`protocolVersion` is an integer. A client or server seeing a higher major version than it knows refuses with a clear message ("Update Bòcan on your Mac"). Additive JSON fields are allowed without a version bump; both sides must ignore unknown fields.
