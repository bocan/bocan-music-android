# Phase Mac-1: Phone Sync Server (implemented in the bocan-music repo)

> This phase is implemented in `bocan-music`, not in this repo. It lives here so the protocol contract and both sides' specs stay together. When implementing, read `bocan-music/docs/design-spec/_standards.md` (the Mac standards apply, not this repo's), `bocan-music/CLAUDE.md`, and this repo's `sync-protocol.md` (normative).
> Depends on: the existing Persistence, Library, Podcasts, and Observability modules in bocan-music.
> Provides: a new `SyncServer` SPM module, settings UI for pairing and sync profiles, and Bonjour-advertised HTTPS serving of the manifest and files.

## Goal

The Mac side of Bòcan Phone Sync: a toggle in Settings that starts a mutual-TLS HTTP server advertised over Bonjour, a pairing sheet that displays the six-digit verification code, a sync-profile picker (what gets synced), and manifest/file endpoints that let a paired phone converge on the chosen library subset. Serving is read-only over the library; this feature writes nothing to tracks, playlists, or podcasts.

## Non-goals

- No phone-to-Mac data flow of any kind (no play counts back, no positions back). One way, forever, by design.
- No WAN/relay/cloud transport. LAN only.
- No overlap with Phase 18's remote-control server: that is a control-plane feature with its own protocol. SyncServer is a separate listener with a separate identity. Do not merge them in v1; note any shared helpers (Bonjour, TLS identity) as candidates for a later refactor.

## Module placement

New module `Modules/SyncServer`, positioned in the DAG alongside the UI-feeding tier:

```
Persistence, Library, Podcasts, Observability -> SyncServer -> UI -> App
```

SyncServer imports Persistence (repositories), Library (SecurityScope for resolving track bookmarks), Podcasts (download paths), and Observability. UI imports SyncServer for the settings surface. Update the DAG diagram in `CLAUDE.md` and `_standards.md` accordingly, and add the module to `project.yml` (then `make generate`).

New log category: `sync` (add to the AppLogger category list and its docs).

## Outcome shape

```
Modules/SyncServer/Sources/SyncServer/
  SyncServer.swift              // actor: lifecycle, NWListener, Bonjour advertising
  Identity/ServerIdentity.swift // P-256 key + self-signed cert in Keychain, fingerprint
  Trust/TrustedDevices.swift    // persisted paired phones, revocation
  Pairing/PairingSession.swift  // ceremony state, nonce, code derivation, rate limits
  Pairing/PairingCode.swift     // same math as the Android PairingCode, golden-vector tested
  Http/HttpConnection.swift     // minimal HTTP/1.1 over NWConnection (constraints below)
  Http/Router.swift             // the 9 endpoints from sync-protocol.md section 6
  Manifest/ManifestBuilder.swift// GRDB snapshot -> Manifest DTOs
  Manifest/SyncProfile.swift    // everything | selected playlists/albums/artists; podcasts on/off
  Files/FileServing.swift       // bookmark-resolved streaming with Range and If-Match
  SyncServerError.swift
Modules/SyncServer/Tests/SyncServerTests/...
Modules/UI/Sources/UI/Settings/PhoneSync/   // settings pane + pairing sheet (localized)
```

## Definitions and contracts

- **Identity**: a P-256 private key and self-signed cert (CN `bocan-mac-<8 hex>`, 25-year validity) created once and stored in the login Keychain via `SecIdentity`. Fingerprint = SHA-256 hex of the cert DER. Exposed for the TXT record and pairing math.
- **Listener**: `NWListener` with `NWProtocolTLS.Options`: local identity set; client certificate requested; the verify block accepts any client cert during pairing mode but tags the connection; outside pairing mode only certs whose fingerprint is in `TrustedDevices` pass. Bonjour advertising via the listener's `service` (`_bocansync._tcp`, TXT `v=1`, `fp=<fingerprint>`, `pm=<0|1>`) so advertising and listening share a lifecycle.
- **HTTP constraints (deliberate, from sync-protocol.md section 5)**: HTTP/1.1 only; requests must carry Content-Length (reject chunked with 411); no keep-alive pipelining requirements (support sequential keep-alive, close on error); responses use Content-Length always. This keeps a hand-rolled parser safe: method line + headers capped at 16 KB, body capped at 1 MB (bodies are only small pairing JSON), unknown methods 405, unknown paths 404. Gzip only for the manifest response and only when requested.
- **ManifestBuilder**: one GRDB read (single `db.read` closure) producing DTOs exactly per sync-protocol.md section 7. Rules:
  - Tracks: only `disabled == false`, only within the sync profile. `rating`, `loved`, ReplayGain, artwork hash, lyricsHash (SHA-256 of the lyrics document it would serve) included. CUE children become `clip` entries referencing `sourceTrackId`.
  - Smart playlists are evaluated through the existing criteria compiler at build time and emitted as ordered id lists; `trackIds` are filtered to tracks present in the manifest (a smart list may reference out-of-profile tracks; drop them).
  - Podcasts: shows with at least one downloaded episode in the profile; episodes only where `downloadState == .downloaded`; `playPositionMs`/`playState` from `podcast_episode_state` at build time; `relPath` mapped from the Downloads directory layout.
  - `generation`: a persisted counter (new small table or UserDefaults-adjacent store in Persistence: prefer a `sync_meta` table via a numbered migration) bumped by a `LibraryChangeObserver` watching GRDB `ValueObservation` on tracks/playlists/podcast tables plus profile edits, debounced 5 s.
- **FileServing**: resolves the track's security-scoped bookmark (via the SecurityScope helper, never raw APIs), streams with a 1 MB read loop, honours `Range: bytes=N-` (206 + Content-Range) and `If-Match` (412 when the current content hash differs from the requested ETag; use the stored `contentHash` and verify mtime unchanged rather than rehashing per request). Artwork served from the cover-art cache by hash; lyrics endpoint assembles the same document the lyrics pane would show (embedded or sidecar or stored), kind `synced` when timestamps exist.
- **Pairing**: implements sync-protocol.md section 4 server-side. `PairingCode` must pass the golden vectors from the Android repo (`bocan-music-android/core/sync/src/test/resources/fixtures/pairing-vectors.json`); copy the file into this module's test fixtures byte-identical. Rate limits: 3 bad proofs or 120 s kills the session; pairing mode auto-exits after success or timeout.
- **Settings UI** (all strings via L10n and the String Catalog; run `make pseudolocale` after adding keys): a "Phone Sync" settings pane: enable toggle, sync profile editor (Everything, or pick playlists; include podcasts toggle; a size estimate), paired devices list (name, paired date, Revoke), and the "Pair a phone" button presenting the pairing sheet: large six-digit code, spinner states, and the final "Pair with '<device>'? Only accept if the phone shows Paired." confirmation (this human step is part of the security design; see the protocol doc).

## Implementation plan

1. `PairingCode` + golden vectors (test-first; the vectors already exist once the Android phase 02 lands, or generate them from `scripts/gen-pairing-vectors.py` in the Android repo and commit to both).
2. `ServerIdentity` (Keychain-backed) with tests for stability and fingerprint format.
3. `HttpConnection` + `Router` with loopback tests (start on 127.0.0.1, drive with URLSession configured to trust the test cert).
4. `TrustedDevices` persistence (small GRDB table via numbered migration, or a JSON file in Application Support; prefer GRDB for consistency) + TLS verify-block integration.
5. `PairingSession` state machine + endpoints.
6. `SyncProfile` model + persistence + `ManifestBuilder` with fixture library DBs; golden manifest JSON fixture shared with the Android repo (`manifest-small.json`) must be producible byte-compatibly (field-for-field; key order may differ, values must not).
7. `FileServing` with Range/If-Match tests.
8. Generation counter + change observer + debounce.
9. `SyncServer` actor tying lifecycle together; starts on app launch if enabled, stops on disable, survives sleep/wake (re-advertise on wake).
10. Settings pane + pairing sheet, localized, snapshot-tested in the UI module per house rules.
11. Docs: README feature section and website page per the repo's commit rules.

## Context7 lookups

- use context7: Network.framework NWListener NWProtocolTLS client certificate verification sec_protocol_options
- use context7: SecIdentity create self-signed certificate SecKeyCreateSignature P-256 Keychain
- use context7: GRDB ValueObservation multiple regions debounce

## Dependencies

None new (Network.framework, CryptoKit, GRDB already present). Entitlement: the app is sandboxed; add the `com.apple.security.network.server` entitlement in this phase (per-feature, as the standards demand).

## Test plan

- PairingCode golden vectors byte-identical with Android.
- Ceremony over loopback TLS: happy path, bad proof x3 lockout, timeout, revocation takes effect on the next connection.
- Router: every endpoint's status codes including 411 on chunked, 404, 405, oversized header rejection.
- ManifestBuilder: profile filtering (playlist selection drops out-of-profile smart-list members), clip tracks, podcast state snapshot, generation bumps on library change (debounced), stable output for identical DB state.
- FileServing: full body, resumed Range, If-Match mismatch 412, bookmark-failure returns 404 with an `op.failed` log, no security-scope leaks (start/stop balanced).
- Concurrency: 3 phones downloading simultaneously does not starve the main actor (server work off the MainActor; assert via test that handlers run on the actor's executor, not main).

## Acceptance criteria

- [ ] Enable Phone Sync, pair a real Android device (or the Android test client) with the code, sync a profile end to end.
- [ ] The manifest validates against `sync-protocol.md` and the shared fixtures; the Android `SyncApplier` accepts it unmodified.
- [ ] Revoke immediately blocks a paired device at the TLS layer.
- [ ] Pairing golden vectors shared and green in both repos.
- [ ] Range resume and If-Match staleness behave per contract (tested).
- [ ] Serving 10 GB to a phone leaves the UI responsive and writes nothing to the library.
- [ ] All new user-facing strings localized; `make pseudolocale` green; snapshot tests updated.
- [ ] `make format`, `make lint`, `make build`, `make test-coverage` green; module coverage floor met.

## Gotchas

- **Never serve a file by path from the request.** Every file endpoint resolves by id through the database and bookmarks; the request never names a path, so traversal is structurally impossible. Keep it that way.
- **Security-scoped resources leak if not balanced**; wrap access in the SecurityScope helper's scoped API and test the balance under early client disconnects.
- **AVAudioFile snapshot semantics do not matter here** (we serve bytes, not decode them), but mid-write files do: skip tracks whose mtime changed since the manifest snapshot (If-Match catches the client side; the server should also re-check before streaming).
- **The generation counter must bump on profile edits** too, not just library changes; a profile change with an unchanged library must trigger phone re-sync.
- **Do not reuse the Phase 18 remote-control identity or port.** Separate concerns, separate trust stores; a phone paired for sync must not gain control-plane access.
- **`pm` TXT flag hygiene**: pairing mode must always revert to `0`, including on error paths; a Mac permanently advertising pairing mode invites drive-by pairing attempts (harmless due to the code check plus human confirm, but noisy).

## Handoff

The Android phases 02 and 03 consume this server. Cross-repo integration is proven by the shared fixtures (pairing vectors, manifest-small) and a documented manual end-to-end run in both PRs.
