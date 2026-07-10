# Phase 09: Scrobbling

> Depends on: phase-04-playback-engine.md (stats event flow)
> Read docs/design-spec/_standards.md first. The Mac reference is `bocan-music/docs/design-spec/phase-13-scrobbling.md`; match its rules.
> Provides: `:core:scrobble`: Last.fm, ListenBrainz, and Rocksky providers with an offline-resilient queue, plus the settings surface.

## Goal

Plays scrobble to the user's chosen services with the classic rules, queue while offline, drain without duplicates, and never block or degrade playback. Parity with the Mac: same eligibility rule, same providers, same resilience posture.

## Non-goals

- No love/unlove write-back in v1 (the Mac owns loved state; a phone-side love would be an edit).
- No scrobble browsing/history UI beyond a recent-submissions list in settings.
- No podcast scrobbling (excluded by flag, matching the Mac).

## Outcome shape

```
core/scrobble/src/main/kotlin/io/cloudcauldron/bocan/scrobble/
  ScrobbleService.kt          // consumes stats events, applies rules, enqueues
  ScrobbleRule.kt             // pure eligibility logic
  queue/ScrobbleQueue.kt      // over ScrobbleDao; backoff, dedup, dead-letter
  providers/{ScrobbleProvider.kt, LastFmProvider.kt, ListenBrainzProvider.kt, RockskyProvider.kt}
  auth/{LastFmAuth.kt, TokenStore.kt}
app/src/main/kotlin/io/cloudcauldron/bocan/app/settings/ScrobbleSettingsScreen.kt
```

## Definitions and contracts

- **Eligibility (`ScrobbleRule`, identical to the Mac)**: track length at least 30 s; scrobble when 50 percent played or 4 minutes, whichever first. Now-playing notifications sent at play start. Podcasts never. One scrobble per continuous play (restart = new candidate).
- `ScrobbleProvider` interface: `updateNowPlaying(item)`, `scrobble(batch)`, `authState: Flow<AuthState>`; errors typed as `retryable` vs `permanent` vs `authExpired`.
- **Queue semantics**: enqueue locally first (the `scrobble_queue` table from phase 01), then attempt delivery; retry with exponential backoff (1 s, 2 s, 4 s ... cap 20 min) on retryable failures; after 10 attempts move to dead-letter (`deadLettered = true`), visible in settings with retry/discard actions. Dedup key: `(provider, trackId, startedAt-rounded-to-minute)`. Batch up to 50 per submission where the API allows.
- **Auth**:
  - Last.fm: mobile session flow (`auth.getMobileSession` with username/password over TLS, or token/web flow via Custom Tabs; prefer the web flow, storing only the session key). API key/secret via `local.properties` -> `BuildConfig`, never committed.
  - ListenBrainz: user token pasted from the website.
  - Rocksky: match the Mac's provider (API key or app password; read `bocan-music/Modules/Scrobble/Sources/Scrobble/Rocksky*` for the exact endpoints when implementing).
  - All credentials in `EncryptedSharedPreferences` (`TokenStore`), redacted in logs.
- Drain triggers: connectivity regained (NetworkCallback), app foreground, and after each new enqueue.

## Implementation plan

1. `ScrobbleRule` pure logic + tests first.
2. Queue over `ScrobbleDao` with backoff/dead-letter state machine.
3. Providers, each against MockWebServer fixtures copied from real API docs (and from the Mac's test fixtures where reusable: `bocan-music/Modules/Scrobble/Tests/.../Fixtures/`).
4. `ScrobbleService` subscribing to the phase 04 stats event flow; per-provider enabled toggles.
5. Settings screen: per-provider connect/disconnect, status line (last submission, queue depth), dead-letter list, master toggle.
6. Wire into `AppGraph`; service lifetime = app process (it is event-driven, no foreground service needed; queued items drain next launch if the process dies).

## Context7 lookups

- use context7: Last.fm API auth.getSession scrobble 2.0 signature requirements
- use context7: ListenBrainz submit-listens API payload format
- use context7: EncryptedSharedPreferences current status and MasterKey setup (check for deprecation and the recommended replacement)

## Dependencies

androidx.security-crypto (or its current replacement per the Context7 check). Test: MockWebServer.

## Test plan

- `ScrobbleRule` table: 29 s track full play -> ineligible; 10 min track at 4 min -> eligible; 6 min track at 50 percent -> eligible; pause does not accumulate; restart resets; podcast flag -> never.
- Queue: offline enqueue x3, connectivity flips, drains in order, exactly once (MockWebServer records); retryable 503 backs off per schedule (virtual time); permanent 400 dead-letters; auth-expired pauses the provider without dropping items.
- Dedup: duplicate event within the rounding window enqueues once.
- Signature test for Last.fm request signing (known-answer from docs).
- No credential ever appears in logs (assert via a capturing AppLog tree).

## Acceptance criteria

- [ ] A real Last.fm account receives a scrobble from a device test (manual, documented in the PR).
- [ ] Offline plays queue and drain without duplicates when connectivity returns.
- [ ] Dead-letter queue visible and actionable in settings.
- [ ] Credentials stored encrypted only; log redaction proven.
- [ ] Podcasts never scrobble.
- [ ] All three providers implemented behind one interface with per-provider toggles.
- [ ] Kover floor holds for `:core:scrobble`.

## Gotchas

- **Clock skew and the dedup key**: use the play's `startedAt` captured at event time, not enqueue time.
- **Last.fm signs requests with an MD5 of sorted params + secret**; the signature excludes `format` and `callback`. Get the known-answer test in before writing the client.
- **Do not scrobble on the audio thread's listener callbacks**; hop to a worker dispatcher immediately.
- **The Mac also scrobbles.** If both devices scrobble the same account, plays on each device are distinct listens; that is correct behaviour, not a dedup bug. Dedup only guards against the phone double-submitting its own plays.

## Handoff

Phase 11's settings hub links to the scrobble settings screen. No later phase depends on internals.
