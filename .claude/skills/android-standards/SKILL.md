---
name: android-standards
description: Reviews Kotlin/Compose code against this repo's binding standards charter, catching the project-specific rules generic review misses. Use for code review, pre-commit checks, or when asked whether code "meets the standards".
allowed-tools: [Read, Glob, Grep, Bash]
---

# Bòcan Android Standards Review

A review lens distilled from `docs/design-spec/_standards.md`. Generic Kotlin taste is not the job; these are the house rules that make or break this codebase. When in doubt, the charter file wins over this summary.

## When This Skill Activates

Use this skill when the user:
- Asks for a review of changed or new code in this repo
- Asks "does this meet the standards?" or "is this ready to commit?"
- Finishes a phase and wants a pre-commit sweep

## Process

1. Identify the changed files (`git diff --name-only` plus staged) and read them.
2. Walk the checklist below against each file. Report findings with `file:line`.
3. Severity: **blocker** (violates a hard rule), **should-fix** (weakens a guarantee), **nit** (style).

## Checklist

### Security and privacy (blockers)
- [ ] No `X509TrustManager` whose `checkServerTrusted` can never throw. The pairing client pins the TXT fingerprint; the paired client pins the stored fingerprint. Anything looser is a Play Store rejection AND a design violation.
- [ ] No credential, token, code, or proof in logs; `AppLog.sensitiveKeys` covers new key names introduced by the change.
- [ ] Credentials only in encrypted storage; nothing secret in Room, DataStore preferences, or plain files.
- [ ] No new permissions without a phase file that demands them. Never `MANAGE_EXTERNAL_STORAGE`.
- [ ] Sync remains one-way: nothing under `core/sync` sends anything but HTTP requests; no endpoint writes phone state to the Mac.
- [ ] `relPath` handling validates against traversal before touching the filesystem.

### Layering (blockers)
- [ ] No upward or sideways module imports: observability < persistence < (sync, playback, scrobble) < app.
- [ ] No Compose imports in `:core:*` modules.
- [ ] Synced Room tables written only by `SyncApplier`; phone-local tables (play_stats, episode_state) never written by sync code except first-time seeding.

### Concurrency
- [ ] Dispatchers injected via the `CoroutineDispatchers` holder, never `Dispatchers.IO` referenced inline.
- [ ] Long loops and per-item Flow work cooperate with cancellation (`ensureActive()`).
- [ ] No `synchronized`, no `runBlocking` outside tests and main().
- [ ] Audio-thread code (AudioProcessors) allocates nothing per buffer and reads settings via volatile snapshots.
- [ ] MediaController/player calls routed through `QueueController` on the main thread.

### Errors and logging
- [ ] Failures cross module boundaries as the module's sealed `*Error` type with context, not bare exceptions.
- [ ] Every `catch` rethrows typed or logs a warning with cause; no bare `runCatching` result ignored.
- [ ] All logging via `AppLog` with `op.start` / `op.end` / `op.failed` naming; no `println`, no `android.util.Log`.

### UI and strings
- [ ] Zero user-facing string literals in composables, notifications, or widgets; everything via `strings.xml` (plurals via `<plurals>`).
- [ ] No em dashes or en dashes anywhere: code, comments, strings, markdown, commit messages.
- [ ] Interactive elements have contentDescription or merged semantics; sliders expose textual values.
- [ ] Lazy lists have stable keys and contentType; images have bounded request sizes.
- [ ] Colors come from theme tokens, never hex literals in composables.
- [ ] No write-implying affordances toward the library (no edit tags, no playlist mutation, no rating input).

### Tests
- [ ] New public functions in `:core` modules have tests; bug fixes include the regression test.
- [ ] No test touches the network (MockWebServer is fine); fixtures checked in, not generated.
- [ ] Flow assertions use Turbine; time uses virtual schedulers, not `Thread.sleep`.

### Build hygiene
- [ ] Dependency versions only in `libs.versions.toml`.
- [ ] No lint/detekt suppressions or coverage-floor changes smuggled in with a feature.

## Output Format

### Blockers
**[file:line]** rule violated, one-line why, suggested fix.

### Should-fix
Same shape.

### Nits
One line each.

### Verdict
"Ready to commit" only when blockers and should-fixes are empty.
