# CLAUDE.md

This file guides Claude Code when working in this repository.

## What this is

Bòcan Music for Android: a Kotlin / Jetpack Compose companion app to the macOS player at `../bocan-music`. It syncs music one way (Mac to phone) over pinned mutual TLS on the local network, and plays it with feature parity where Android allows. It is a player only: it never edits files, tags, or playlists.

## Project skills

Three repo skills in `.claude/skills/` encode the workflow; prefer them over improvising:

- `/phase NN` implements one design-spec phase end to end (binding docs, contracts, gates, acceptance boxes, conventional commit).
- `/android-standards` reviews changes against the standards charter before committing.
- `/protocol-guard` runs whenever `core/sync`, the manifest DTOs, shared fixtures, or `sync-protocol.md` change; it classifies contract impact and flags Mac-side counterparts.

## How to work here

- The build is phased. Implement exactly one phase file from `docs/design-spec/` per session, in order, honouring each file's `Depends on:` header.
- Read `docs/design-spec/_standards.md` before writing any code. It is binding.
- `docs/design-spec/sync-protocol.md` is the wire contract shared with the Mac side. Never change protocol behaviour without updating that document first; the Mac implementation in `../bocan-music` follows the same file.
- Module DAG (no upward imports): `:core:observability` -> `:core:persistence` -> `:core:sync` / `:core:playback` / `:core:scrobble` -> `:app`.
- Logging goes through the `AppLog` facade only. No `println`, no raw `Log.d`.
- Tests must not hit the network. Use MockWebServer or fakes. Fixtures are checked in under each module's `src/test/resources/fixtures/`.
- Conventional Commits, scope = module: `feat(playback): ...`, `fix(sync): ...`. One logical change per commit.
- Never use em dashes or en dashes in code comments, commit messages, markdown, or UI strings. Use commas, colons, parentheses, or plain hyphens.
- All user-facing strings live in `strings.xml` resources. No hardcoded literals in composables.

## Commands

Once phase 00 lands, everyday commands go through Gradle:

| Task | Command |
|------|---------|
| Debug build | `./gradlew assembleDebug` |
| Unit tests, all modules | `./gradlew test` |
| Single module tests | `./gradlew :core:sync:test` |
| Lint (ktlint + detekt + Android lint) | `./gradlew check` |
| Coverage | `./gradlew koverHtmlReport` |
| Install on device | `./gradlew installDebug` |
