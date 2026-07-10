---
name: protocol-guard
description: Guards the Bòcan Sync Protocol contract whenever wire-adjacent code changes. Use before committing changes to core/sync networking, pairing, manifest DTOs, endpoints, or when anyone proposes changing sync-protocol.md.
allowed-tools: [Read, Glob, Grep, Bash]
---

# Sync Protocol Guard

`docs/design-spec/sync-protocol.md` is a two-repo contract: this repo's `:core:sync` and the Mac's `SyncServer` module implement the same document. Drift between code and contract, or between the two repos, is the highest-severity bug class this project has. This skill exists to catch it before it lands.

## When This Skill Activates

Use this skill when:
- Anything changes under `core/sync/` networking, pairing, or engine code
- The manifest DTOs in `core/persistence` (`model/manifest/`) change
- Someone proposes editing `sync-protocol.md` itself
- Shared fixtures change: `pairing-vectors.json`, `manifest-small.json`
- Debugging a sync interop failure between the phone and the Mac

## Process

### 1. Classify the change

Read the diff (`git diff` plus staged) and sort each change into:

- **A. Implementation-only**: behaviour the contract already specifies, expressed differently in code. No contract impact.
- **B. Contract-affecting**: new/changed endpoint, field, status code, TXT key, pairing math, TLS behaviour, sync-algorithm ordering, or error code.
- **C. Contract edit**: a change to `sync-protocol.md` itself.

### 2. For A: verify conformance

Check the code against the contract clause by clause. High-value spot checks:

- Pairing math matches section 4 step 3 exactly (fingerprint min/max ordering, nonce concatenation order, the `bocan-pair-v1` literal, first 8 bytes, mod 1,000,000, zero-padding).
- Fingerprints are computed from the TLS-layer certificate, never from JSON fields.
- The pairing client pins the TXT `fp`; the paired client pins the stored fingerprint; neither can accept an arbitrary cert.
- File requests send `If-Match` and handle `412` as manifest-stale; `Range` format matches section 6.
- The differ treats `id` as identity and `sha256` as change detection; metadata-only changes cause no transfer.
- Transfer-before-apply ordering preserved; deletes only after the transaction commits.
- Local-state tables are never written by manifest application except first-time seeding.
- Unknown JSON fields are ignored (`ignoreUnknownKeys`); nothing throws on additive fields.

### 3. For B: stop and route through the contract

A contract-affecting code change without a contract edit is a blocker, full stop. Report it and require:

1. Edit `sync-protocol.md` first. Additive JSON fields: allowed, document them. Behavioural or breaking changes: bump `protocolVersion` and document the compatibility rule.
2. Flag the Mac-side counterpart: name the file(s) in `../bocan-music/Modules/SyncServer/` that must change, and note it in the commit body as `requires bocan-music: <summary>`.
3. Update shared fixtures if the wire bytes change, and state that the copies in both repos must remain byte-identical.

### 4. For C: review the contract edit itself

- Is the change additive (safe) or breaking (needs `protocolVersion` bump and a refusal story per section 10)?
- Does the edit keep both implementations implementable (no platform-only assumptions)?
- Are fixtures and golden vectors regenerated where affected?
- Does the phase-mac-1 spec need a matching edit?

### 5. Fixture integrity check

If either shared fixture is in the diff, verify (or instruct) that the twin copy is updated:

```
shasum core/sync/src/test/resources/fixtures/pairing-vectors.json
shasum ../bocan-music/Modules/SyncServer/Tests/SyncServerTests/Fixtures/pairing-vectors.json
```

Hashes must match (same for `manifest-small.json` wherever both repos hold it). If the Mac repo copy does not exist yet (phase-mac-1 not built), note it instead of failing.

## Output Format

- **Classification**: A / B / C per changed area
- **Conformance findings**: contract clause vs code, `file:line`, verdict
- **Blockers**: any B without a contract edit; any fixture divergence
- **Cross-repo actions**: exact follow-ups needed in `../bocan-music`
- **Verdict**: safe to commit / blocked, one line why

## Hard rules

- The contract wins over the code, always. If the code is better, change the contract first, then the code.
- Never weaken the security posture to fix an interop bug: no trust-manager loosening, no auth-optional endpoints, no acceptance of unpinned certs "temporarily".
- One-way sync is constitutional. Any change that sends phone state to the Mac is rejected regardless of how useful it looks.
