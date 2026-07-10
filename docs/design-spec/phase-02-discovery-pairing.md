# Phase 02: Discovery and Pairing

> Depends on: phase-00-foundations.md, phase-01-persistence.md
> Read docs/design-spec/_standards.md and docs/design-spec/sync-protocol.md first. The protocol document is normative; this phase implements its sections 1 through 6 (client side).
> Provides: `:core:sync` identity, discovery, trust, and pairing; plus the pairing screens in `:app`.

## Goal

The phone can find a Mac running Bòcan's Phone Sync on the local network, run the pairing ceremony from sync-protocol.md section 4 end to end, and persist a pinned mutual-TLS relationship. After this phase, a paired `OkHttpClient` factory exists that later phases use for everything.

## Non-goals

- No manifest handling, no downloads (phase 03).
- No multi-Mac support in the UI (the trust store supports it; the UI pairs with one Mac in v1).

## Outcome shape

```
core/sync/src/main/kotlin/io/cloudcauldron/bocan/sync/
  identity/DeviceIdentity.kt        // Keystore key + self-signed cert, fingerprints
  discovery/MacDiscovery.kt         // NsdManager wrapper -> Flow<List<DiscoveredMac>>
  pairing/PairingClient.kt          // the ceremony state machine
  pairing/PairingCode.kt            // the HMAC code derivation, pure function
  net/SyncHttpClientFactory.kt      // pre-pairing and paired OkHttpClient builders
  net/TrustStore.kt                 // persisted peer cert via SyncDao
  SyncError.kt
app/src/main/kotlin/io/cloudcauldron/bocan/app/pairing/
  PairingScreen.kt  PairingViewModel.kt  CodeEntryField.kt
```

## Definitions and contracts

```kotlin
data class DiscoveredMac(
    val serviceName: String,
    val host: InetAddress,
    val port: Int,
    val fingerprint: String,   // TXT fp
    val pairingMode: Boolean,  // TXT pm == "1"
    val protocolVersion: Int,  // TXT v
)

object PairingCode {
    /** sync-protocol.md section 4 step 3. Pure, fully unit-testable. */
    fun derive(fpMac: String, fpPhone: String, noncePhone: ByteArray, nonceMac: ByteArray): String // "123456"
    fun confirmProof(code: String, sessionId: String): ByteArray  // HMAC-SHA256(key = code ASCII, msg = sessionId ASCII)
}

sealed interface PairingState {
    data object Discovering : PairingState
    data class AwaitingCode(val mac: DiscoveredMac, val expectedCode: String) : PairingState
    data class Confirming(val mac: DiscoveredMac) : PairingState
    data class Paired(val serverName: String) : PairingState
    data class Failed(val error: SyncError) : PairingState
}
```

`DeviceIdentity`: generates a P-256 key in the Android Keystore (`KeyProperties.PURPOSE_SIGN`, no user auth requirement) and a self-signed cert (25-year validity, CN `bocan-android-<8 hex>`) on first use; exposes `certificate: X509Certificate`, `fingerprint: String`, and a `KeyManager` for TLS client auth. Certificate generation uses BouncyCastle's `X509v3CertificateBuilder` signed by the Keystore key via `ContentSigner` bridging, or, simpler and preferred if workable, `android.security.keystore` attestation-free self-signed flow; investigate with Context7 and pick the least exotic path that keeps the private key non-exportable.

`SyncHttpClientFactory`:
- `pairingClient(expectedFingerprint)`: TLS whose trust manager verifies that the presented server certificate's SHA-256 equals the fingerprint from the discovered TXT record, capturing the certificate for the ceremony, and only permitting the `/v1/pair/*` and `/v1/ping` paths at the interceptor level, presenting the device client cert. The trust manager must throw `CertificateException` on any mismatch. There must be no accept-any trust manager anywhere in the codebase: Google Play's pre-launch security scan flags `checkServerTrusted` implementations that never throw, and that is a store rejection, not a warning. The TXT fingerprint is unauthenticated, so this pin is defence in depth; the pairing code remains the real MITM check.
- `pairedClient()`: pins the stored `fpMac` (reject on mismatch before any request body is sent), presents the device cert, TLS 1.3 preferred. Also sets sane timeouts (connect 5 s; read: long for file streams, override per-call in phase 03).

## Implementation plan

1. `DeviceIdentity` with tests (Robolectric or instrumented-lite: fingerprint stability across constructions, cert parses, CN format).
2. `PairingCode.derive` exactly per protocol: lexicographic min/max of hex fingerprints, `key = noncePhone || nonceMac`, `msg = "bocan-pair-v1" || fpLo || fpHi`, first 8 HMAC bytes as unsigned big-endian mod 1,000,000, zero-padded to 6 digits. Write the golden-vector test FIRST (see test plan) because the Mac implements the same vectors.
3. `MacDiscovery`: `NsdManager.discoverServices("_bocansync._tcp", ...)` wrapped in `callbackFlow`, resolving each service to read TXT records, debounced into a `Flow<List<DiscoveredMac>>`. Handle the notorious NsdManager one-resolve-at-a-time limitation by serialising resolves through a queue. Registers/unregisters with lifecycle.
4. `TrustStore` over `SyncDao` (`sync_server` table): save and load the paired server row; expose `isPaired: Flow<Boolean>`.
5. `PairingClient` state machine implementing the ceremony: start -> POST `/v1/pair/start` -> compute expected code -> surface `AwaitingCode` -> user submits typed code -> compare locally -> on match POST `/v1/pair/confirm` with proof -> persist trust -> `Paired`. Three wrong local entries or a `pairingExpired`/`badProof` response fails the session with a distinct, human-readable `SyncError` case per protocol machine code.
6. Pairing UI in `:app`: a screen reachable from Settings and from first-run: list of discovered Macs in pairing mode, tap to start, six-digit code entry (numeric keypad, auto-advance boxes), success and failure states. The mismatch failure copy must include the warning from the protocol doc verbatim ("This code does not match...").
7. Wire into `AppGraph`.

## Context7 lookups

- use context7: NsdManager discoverServices resolve TXT records callbackFlow pattern and Android 14 changes
- use context7: OkHttp custom SSLSocketFactory X509TrustManager client certificates and certificate pinning by SHA-256
- use context7: Android Keystore EC key generation and using a Keystore key to sign an X509 certificate (BouncyCastle ContentSigner)

## Dependencies

OkHttp, BouncyCastle (bcpkix) if needed for cert building, kotlinx-serialization (already present). Test: MockWebServer with TLS (`HandshakeCertificates` from okhttp-tls).

## Test plan

### PairingCode golden vectors (shared with the Mac repo, checked in as `fixtures/pairing-vectors.json`)
- At least 4 vectors of (fpMac, fpPhone, noncePhone, nonceMac, expectedCode, sessionId, expectedProofBase64). Generate them once with a reference script (put `scripts/gen-pairing-vectors.py` in this repo, deterministic seeds), and copy the file to the Mac repo when phase-mac-1 is built. Both implementations must pass the identical file.
- Property: swapping fpMac/fpPhone yields the same code (min/max normalisation). Different nonces yield different codes.

### Ceremony against MockWebServer (TLS)
- Happy path end to end: paired row persisted with the server's real cert fingerprint from the TLS layer, not from JSON.
- Wrong code typed: no confirm request is ever sent; recorded cert discarded.
- Server returns `badProof` / `pairingExpired` / 503: correct typed errors.
- MITM simulation: MockWebServer presents cert A while the expected-code derivation is fed cert B's fingerprint; typed "Mac's code" mismatches; ceremony aborts. (This is the security property; encode it as a named test.)
- TXT pin: the pairing client refuses a server whose certificate does not hash to the advertised fingerprint (`CertificateException` before any request is sent).

### Discovery
- Fake `NsdManager` seam (interface wrapper) proving resolve-queue serialisation and TXT parsing, including missing keys.

## Acceptance criteria

- [ ] Golden pairing vectors pass; the vectors file and generator script are committed.
- [ ] Full ceremony succeeds against a TLS MockWebServer, persisting the pinned relationship.
- [ ] The paired client refuses a server presenting a different certificate (test proves the request fails before sending).
- [ ] The pre-pairing client refuses to call non-pairing endpoints.
- [ ] No accept-any `X509TrustManager` exists in the app: both client builders throw on certificate mismatch, proven by test (Google Play's security scanner rejects accept-any implementations).
- [ ] Pairing UI: discovery list, code entry, success, and the mismatch warning all reachable; strings in `strings.xml`.
- [ ] No secrets or codes appear in logs (AppLog redaction covers `code` and `proof`).
- [ ] Kover floor holds for `:core:sync` so far.

## Gotchas

- **Take fingerprints from the TLS session, never from JSON.** The JSON fields are advisory; the security property depends on hashing the certificate the socket actually presented.
- **Never write a trust manager that cannot fail.** Even the pairing-mode client pins to the TXT-advertised fingerprint. An accept-any `checkServerTrusted` is both a Play Store rejection and a habit this codebase refuses to form.
- **NsdManager resolves one service at a time** and throws `FAILURE_ALREADY_ACTIVE` if you overlap; serialise resolves.
- **Keystore keys and TLS**: an Android Keystore private key cannot be extracted, so the `KeyManager` must delegate signing to the Keystore. Do not "fix" a handshake problem by generating the key outside the Keystore and storing it in a file.
- **The code is not a secret** but redact it in logs anyway; log hygiene should not depend on the reader knowing the crypto design.
- **Wi-Fi multicast**: mDNS discovery on some devices needs a `WifiManager.MulticastLock` held during discovery. Acquire narrowly, release always.

## Handoff

Phase 03 assumes: `SyncHttpClientFactory.pairedClient()` working, `TrustStore.isPaired`, `MacDiscovery` emitting known paired Macs (fingerprint match), and typed `SyncError` cases for every protocol failure.
