---
name: phase
description: Implements one design-spec phase of Bòcan for Android end to end, from reading the binding docs through green gates and a conventional commit. Use when asked to implement, build, or continue a phase (e.g. "/phase 03" or "implement phase-03-sync-engine").
allowed-tools: [Read, Write, Edit, Glob, Grep, Bash]
---

# Phase Runner

Builds exactly one phase from `docs/design-spec/`, the way this repo's methodology demands: contracts honoured, tests written, gates green, acceptance boxes ticked, one conventional commit per logical change.

## When This Skill Activates

Use this skill when the user:
- Says "/phase NN" or names a phase file to implement
- Asks to start, continue, or finish a design-spec phase
- Asks "what's the next phase?" (answer from the index, then offer to run it)

## Process

### 1. Load the binding context, in this order

1. `docs/design-spec/_standards.md` (binding on all code)
2. The phase file itself, end to end
3. `docs/design-spec/sync-protocol.md` IF the phase touches discovery, pairing, sync, manifest DTOs, or anything in `core/sync` (phases 02, 03, and mac-1 always; others as needed)
4. The `Depends on:` header: verify each prerequisite phase's acceptance boxes are ticked in its file. If any are unticked, stop and tell the user which prerequisite is incomplete.

### 2. Confirm scope before writing code

- Restate the Goal and Non-goals in one sentence each.
- List the files you will create or edit (compare against the phase's Outcome shape).
- If the phase file's contract conflicts with reality (an API changed, a dependency version moved, a prerequisite drifted), STOP and surface the conflict. Never silently deviate from a contract; the spec is corrected first, then the code.

### 3. Implement

- Follow the phase's Implementation plan step order. Each numbered step should be a committable unit.
- Contracts given as code blocks in the phase file (signatures, schemas, endpoints) are verbatim requirements, not suggestions.
- Where the phase says test-first (golden vectors, rules tables, parsers), write the failing test before the implementation.
- Run the phase's Context7 lookups before using any evolving API (Media3, Room, WorkManager, Compose, OkHttp, Coil, Glance). Latest stable wins; note deviations from pinned versions in the commit body.
- Every user-facing string goes in `strings.xml`. Every log line goes through `AppLog`. No em dashes or en dashes anywhere, including comments and commit messages.

### 4. Gates

Run and pass, in order:

```
./gradlew check          # ktlint + detekt + Android Lint
./gradlew test           # all unit tests
./gradlew koverVerify    # coverage floors
./gradlew assembleDebug  # it must actually build an APK
```

Do not proceed past a red gate by weakening a rule, adding a suppression, or lowering a floor. Fix the code. If a rule is genuinely wrong, stop and ask.

### 5. Close out

- Work through the phase's Test plan buckets; every listed case has a corresponding test.
- Tick each satisfied `- [ ]` acceptance box in the phase file itself (edit the file to `- [x]`). Boxes you cannot satisfy stay unticked with a one-line reason added beneath them; tell the user.
- Verify the Handoff section's promises are true.
- Commit with Conventional Commits, scope = module (`feat(sync): ...`, `feat(playback): ...`). One logical change per commit. No AI attribution trailers of any kind.

## Output Format

End with a short report:

- **Phase**: name and status (complete / blocked)
- **Landed**: files created or changed, one line each
- **Gates**: each command and its result
- **Acceptance**: ticked N of M, listing any unticked with reasons
- **Handoff**: what the next phase can now rely on

## Hard rules

- One phase per session. If asked to start a second, recommend a fresh session.
- Never edit `sync-protocol.md` casually; protocol changes route through the protocol-guard skill's checklist.
- Never touch files under `assets/icons/` (brand assets, synced from the Mac repo).
- If the phase is mac-1, stop: it is implemented in `../bocan-music` under that repo's standards, not here.
