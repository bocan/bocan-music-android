# Phase 16: Trunk-Based Development and Hand-Cut Releases

> Depends on: phase-12-release.md (the signing, versionCode, 16 KB, and Play publishing machinery it keeps), phase-14-demo-library.md and later PRs only in that they must land through the new gates.
> Read docs/design-spec/_standards.md first. The Mac repo's ADR-033 (`../bocan-music/docs/design-spec/ADR-033-*.md`) is the reference design; this phase ports it, with the differences an Android app forces called out.
> Provides: the same branch, PR, changelog, and release process as `bocan-music`, release-please removed, human-written release notes, and one full development cycle run end to end to prove the machinery.

## Goal

Make this repo work exactly the way the Mac repo now works. `main` is a trunk nobody commits to. Every change lands by a squash-merged pull request whose title is a Conventional Commit. A user-visible change carries its own release note, written for a listener, in the same PR, under `## [Unreleased]` in `CHANGELOG.md`, and a check refuses the PR without one. A release is a decision: the maintainer starts the Release workflow by hand, a script decides the version from the squash subjects since the last tag, rewrites the changelog and stamps the version, lands that as a release PR through the same gates, tags the merge, builds and signs the APK and AAB, and publishes a GitHub release whose body is the prose only. release-please and its bot PRs go away. The phase is done only after a real cycle has run: a branch, a PR refused for a missing note, the note added, the merge, the Release workflow, the tag, and a signed APK on the release page.

## Non-goals

- No nightly or beta channel, release trains, or dashboards (ADR-033 "Not doing").
- No reusable workflows or composite actions; three small YAML files are the whole thing.
- No Play Console automation beyond what phase 12 prepared (the dormant internal-track upload stays as it is, gated on its secret).
- No rewriting of the historical `CHANGELOG.md` entries. The release-please sections stay below the new `## [Unreleased]` heading; only new sections use the new shape.
- No change to the versionCode formula, the signing keystore handling, the 16 KB gate, or the serial coverage gate. They move between workflow files unchanged.

## Outcome shape

```
.github/workflows/
  branch.yml                 // NEW: push to any branch but main: gitleaks, ktlint, detekt, assembleDebug
  pr.yml                     // NEW: pull request to main: the full suite; docs-only PRs skip the build job
  pr-metadata.yml            // NEW: Conventional Commit title; release note required for feat/fix/perf
  release.yml                // REWRITTEN: manual only; prepare (version, changelog, release PR, tag) then build (sign, attach, publish)
  ci.yml                     // DELETED (split into branch.yml and pr.yml)
  release-please.yml         // DELETED
release-please-config.json   // DELETED
.release-please-manifest.json // DELETED
scripts/
  release.sh                 // NEW: next | preview | apply (version from squash subjects; CHANGELOG, build.gradle.kts, version.txt)
  release-notes.sh           // NEW: the prose for one version, plus a Play "What's new" length warning
  install-hooks.sh           // EXTENDED: gitleaks on staged changes before ktlint and detekt
  tests/release-test.sh      // NEW: fixture-repo tests for release.sh (bash, runs on Linux in pr.yml)
  tests/release-notes-test.sh
Makefile                     // release-preview, test-scripts, hooks (installs gitleaks check), ci target split
CHANGELOG.md                 // gains "## [Unreleased]" at the top; history untouched
app/build.gradle.kts         // the version line loses its x-release-please marker; release.sh stamps it
version.txt                  // stamped by release.sh (kept: the About screen and scripts read it)
CLAUDE.md                    // Branching, Release notes, Commits sections ported from the Mac repo
CONTRIBUTING.md              // NEW: the PR conventions in one page
docs/release-checklist.md    // "How the pipeline works" rewritten; per-release steps
docs/design-spec/phase-12-release.md   // a note at the top: versioning and release flow superseded by phase 16
.claude/skills/phase/SKILL.md          // close-out step: push the branch and open the PR; never commit on main
```

Repository settings (done by hand in GitHub, recorded in this file's Migration section when done): squash merging only, squash title = PR title, squash message = PR description, delete branch on merge; branch protection on `main`: PR required, required checks `Build & Test`, `Script tests`, `Conventional Commit title`, `Release note in CHANGELOG Unreleased`, strict, admins included, linear history, no force pushes. Labels: create `skip-changelog`; delete `autorelease: pending` and `autorelease: tagged`. Secrets: `RELEASE_TOKEN` (fine-grained PAT, Contents and Pull requests read/write on this repo).

## What carries over from previous specs

- Phase 12's `release.yml` build steps: decode keystore, `assembleRelease` and `bundleRelease`, the 16 KB alignment check, staging `bocan-music-<version>.apk`, `.aab`, `checksums.txt`, and the dormant Play upload. They become the `build` job of the new workflow, unchanged apart from where the version and notes come from.
- `versionCodeOf` in `buildSrc` and its test. The release script stamps `versionName`; the code is still derived.
- `ci.yml`'s two-step coverage discipline (parallel `check test assembleDebug -x koverVerify -x koverCachedVerify`, then `koverVerify --no-parallel --rerun-tasks`) moves into `pr.yml` verbatim, with its comment.
- The pre-commit hook from phase 00 (`scripts/install-hooks.sh`, `make hooks`) keeps ktlint and detekt and gains gitleaks in front of them.
- `docs/release-checklist.md` keeps every manual gate; only the pipeline description and the "how to cut a release" steps change.
- Conventional Commits with module scope (`_standards.md`) stay the rule for every commit on a branch. What is new is that the PR title becomes the one commit on `main`, so the title is what the version rule reads.

## Implementation plan

Each numbered step is one branch and one PR, in this order. The first three can be reviewed on their own; nothing breaks until step 4 deletes the old workflows.

1. **Branch CI and the hook.** Add `branch.yml`: on push to any branch except `main`, `paths-ignore` for `**.md` and `docs/**`; job `secrets` runs gitleaks over `origin/main..HEAD`; job `build` runs `./gradlew ktlintCheck detekt assembleDebug`. No repository secrets. Extend the pre-commit hook with gitleaks on staged changes (fail with an install hint if gitleaks is missing; `make doctor` reports it). Add the Branching section to `CLAUDE.md`. Set the repo to squash-only with delete-branch-on-merge.
2. **PR CI and metadata.** Add `pr.yml` with a `changes` job (docs-only PRs skip `Build & Test`, which then counts as passed), a `scripts` job running `scripts/tests/*.sh`, and `build-and-test` carrying every step of today's `ci.yml` build job plus the `buildSrc` test, the unsigned `assembleRelease`, the 16 KB gate, and the serial coverage step. Add `pr-metadata.yml`: the title check (types `feat fix perf refactor docs chore ci build test style revert`, optional scope, optional `!`, em dash rejected) and the changelog check (a `feat`, `fix`, or `perf` PR must add non-blank lines inside `## [Unreleased]`, unless labelled `skip-changelog`; the added lines must not contain backticks, `(#NNN)`, or code vocabulary: `refactor`, `refactored`, `implemented`, `ViewModel`, `Repository`, `Dao`, `Composable`, `Flow`, `suspend`, `coroutine`, `Gradle`). Add `## [Unreleased]` to `CHANGELOG.md`. Create the `skip-changelog` label. Enable branch protection with the four required checks. Delete `ci.yml` in the same PR so the required check names are stable from day one.
3. **The release script.** `scripts/release.sh next|preview|apply` ported from the Mac with three Android differences: it stamps `val appVersionName = "X.Y.Z"` in `app/build.gradle.kts` (the marker comment goes) and `version.txt`; there is no plist; and `apply` refuses (exit 5) if `versionCodeOf` would reject the new version (minor or patch above 99). Starting Gradle for that check is too slow for a script, so the two-digit rule is repeated in bash and the fixture test pins it to the Kotlin one. `scripts/release-notes.sh` ported as is, plus a warning on stderr when the prose exceeds 500 characters, the Play Console "What's new" limit. `scripts/tests/release-test.sh` and `release-notes-test.sh` ported to a fixture repo with a `build.gradle.kts` and `version.txt` instead of a plist. `make release-preview` and `make test-scripts`. Tests run in `pr.yml`'s `scripts` job.
4. **The Release workflow.** Rewrite `release.yml` as manual only (`workflow_dispatch`, optional `tag` input to rebuild). `prepare` (Linux): require `RELEASE_TOKEN`; `release.sh next` (exit 3 means nothing to release, fail with that message); `release.sh apply`; commit `chore(release): X.Y.Z` on `release/vX.Y.Z`; open the PR with the prose as its body; `gh pr checks --watch --fail-fast`; `gh pr merge --squash --delete-branch`; tag the merge commit `vX.Y.Z`. `build` (Linux, needs the keystore secret, else skipped with a notice): checkout the tag; require the `## [X.Y.Z]` section; JDK 21 and Gradle; decode the keystore; `assembleRelease` and `bundleRelease`; 16 KB gate; stage artifacts and checksums; build-provenance attestation of the APK and AAB (`actions/attest-build-provenance`); `gh release create vX.Y.Z --title X.Y.Z --notes-file <prose>` with the artifacts attached, prerelease when the tag carries `-beta` or `-rc`; the dormant Play upload step, which writes the prose to the `whatsnew` file the publisher plugin reads. Delete `release-please.yml`, its config, and its manifest. Delete the two `autorelease` labels. Close the bot's open release PR if one exists.
5. **Docs and skills.** `CLAUDE.md` gains the Release notes and Commits sections from the Mac repo, adapted (the pre-commit hook runs gitleaks, ktlint, and detekt; the gates are `make lint`, `make test`, `make build`). `CONTRIBUTING.md` new. `docs/release-checklist.md` "How the pipeline works" rewritten in the ADR-033 shape (branch, PR, hand-cut release, no CI commits to main). Phase 12 gets a superseded note. The `/phase` skill's close-out step changes from "commit" to "commit on the branch, push, open the PR with the release note in the body and in `CHANGELOG.md`". The memory about device installs stays true: the signed APK still comes from the GitHub release.
6. **The proving cycle.** With every step above merged and the `RELEASE_TOKEN` and keystore secrets in place, run one real cycle and record it in this file's Migration section with PR and run links:
   1. Open a `fix` or `feat` PR with no changelog note. The `Release note in CHANGELOG Unreleased` check must fail and block the merge.
   2. Add a note that contains a backtick. The check must fail again with the "reads like a commit message" message.
   3. Fix the note. All four checks green. Squash-merge. `main` shows one commit with the PR title.
   4. Open a docs-only PR. `Build & Test` is skipped and the PR is mergeable.
   5. `make release-preview` locally shows the version and the section. Run Actions, Release, with no input. `prepare` opens `chore(release): X.Y.Z`, waits, merges, tags. `build` attaches `bocan-music-X.Y.Z.apk`, `.aab`, and `checksums.txt` to a release whose body is the prose and a "Full changelog" link.
   6. Install that APK on the phone. The About screen shows `X.Y.Z`.
   7. Run the Release workflow again with the tag as input. `prepare` is skipped, `build` rebuilds and re-attaches.
   8. Merge a `chore` only PR and run Release again. `prepare` must fail with "nothing to release".

## Definitions and contracts

**Version rule** (`scripts/release.sh`, first-parent history since the last `v*` tag, one line per squash-merged PR): `!` or `BREAKING CHANGE` in the body gives major; `feat` gives minor; `fix` or `perf` gives patch; anything else only means no release (exit 3). The app is pre-1.0; the first breaking change deliberately makes it 1.0.0, so use `!` only when that is meant.

**CHANGELOG section shape** (identical to the Mac):

```
## [X.Y.Z](https://github.com/bocan/bocan-music-android/compare/vP.Q.R...vX.Y.Z) (YYYY-MM-DD)

<the maintainer's prose from Unreleased, verbatim>

### For developers

**Added**
- scope: subject ([#NNN](https://github.com/bocan/bocan-music-android/pull/NNN))
**Fixed**
...
```

`apply` refuses with exit 4 when the prose is empty. `release-notes.sh <version>` prints the prose and the compare link only, exit 1 when the section or the prose is missing.

**Release note rules** (`CLAUDE.md`, enforced by `pr-metadata.yml`): one or two plain sentences per change, present tense, what is different for the person holding the phone. No class, module, or function names, no backticks, no PR numbers, no "refactored" or "implemented". A feature may take a short paragraph. Keep the whole `Unreleased` block under 500 characters when you can; longer is allowed, but Play shows only the first 500.

**Exit codes** (`release.sh`): 0 ok; 1 environment (no tag, no changelog); 2 usage; 3 nothing to release; 4 empty prose; 5 version not representable by `versionCodeOf`.

**Branch names**: `<type>/<slug>` where type is the Conventional Commit type the PR will carry (`feat/`, `fix/`, `chore/`, `docs/`, `refactor/`, `ci/`), optionally with an issue number.

**Required checks**: `Build & Test` (skipped counts as passed for docs-only PRs), `Script tests`, `Conventional Commit title`, `Release note in CHANGELOG Unreleased`. Dependabot PRs are `build(deps):` and need no note.

## Context7 lookups

- use context7: GitHub Actions actions/checkout v7 fetch-depth full history for git describe
- use context7: gh pr checks --watch --fail-fast and gh pr merge --squash in a workflow with a PAT
- use context7: actions/attest-build-provenance subject-path multiple files
- use context7: gitleaks git --pre-commit --staged and --log-opts usage
- use context7: Gradle Play Publisher whatsnew release notes file layout and 500 character limit
- use context7: GitHub branch protection required status checks with a skipped job

## Dependencies

No new Gradle dependencies. Developer machines need `gitleaks` (`brew install gitleaks`; `make doctor` checks). CI uses the gitleaks release tarball pinned by version in `branch.yml`. GitHub-side prerequisites, done by Chris: the `RELEASE_TOKEN` secret, the keystore secrets from phase 12 (`BOCAN_KEYSTORE_BASE64` and friends) so the proving cycle produces a signed build, and the repository settings listed under Outcome shape.

## Test plan

- `scripts/tests/release-test.sh` (fixture repo, bash, Linux and macOS): `next` gives patch for `fix`, minor for `feat`, major for `feat!` and for a `BREAKING CHANGE` body, exit 3 for `chore` only, first-parent only (a merged branch's inner commits are ignored); `preview` writes prose first and grouped subjects with PR links; `apply` rewrites `CHANGELOG.md`, `build.gradle.kts`, and `version.txt` and leaves an empty `Unreleased`; exit 4 on empty prose; exit 5 on a version with minor 100; idempotent `next` after `apply` and a new tag.
- `scripts/tests/release-notes-test.sh`: prints prose and compare link; exit 1 on a missing section; warns over 500 characters.
- `buildSrc` `versionCodeOf` test unchanged, still run in `pr.yml`.
- Workflow behaviour is tested by the proving cycle in step 6, which is the acceptance list below, and by one negative run of each check.

## Acceptance criteria

- [ ] `main` has branch protection with the four required checks; squash-only; delete branch on merge; `skip-changelog` label exists; `autorelease` labels gone.
- [ ] A `fix` PR without a note is refused; with a backtick in the note it is refused; with a plain note it passes.
- [ ] A docs-only PR skips the build and is mergeable.
- [ ] `release-please.yml`, its config and manifest are deleted; no bot PR is open.
- [ ] The Release workflow, started by hand with no input, produces a `chore(release): X.Y.Z` PR, merges it, tags `vX.Y.Z`, and publishes a release with the prose body and signed `.apk`, `.aab`, and `checksums.txt` attached, with a provenance attestation.
- [ ] The installed release shows `X.Y.Z` in About.
- [ ] Rebuilding an existing tag re-attaches artifacts without a new tag; a `chore` only trunk makes `prepare` fail with "nothing to release".
- [ ] `make release-preview`, `make test-scripts`, and `make hooks` (with gitleaks) work locally.
- [ ] `CLAUDE.md`, `CONTRIBUTING.md`, `docs/release-checklist.md`, phase 12's note, and the `/phase` skill describe the new process and nothing describes the old one.
- [ ] The Migration section below records every step's PR and the proving cycle's run links.

## Gotchas

- **Ordering matters.** Deleting `ci.yml` before `pr.yml` exists leaves `main` unprotected by any check; adding branch protection before `pr.yml` exists blocks every PR forever, because a required check that never starts never passes. Step 2 does both in one PR and turns protection on right after that PR merges.
- **A `GITHUB_TOKEN`-opened PR gets no checks.** That is why `prepare` needs `RELEASE_TOKEN`; without it the release PR can never merge and the workflow hangs at `--watch`. Fail early if the secret is empty.
- **The open PRs at the time of migration** were written with per-commit messages. Under squash-only, each one's title becomes its commit; the titles are already Conventional. The `feat` and `fix` ones need an `Unreleased` note before they can merge once step 2 lands; add the notes in those PRs rather than labelling them `skip-changelog`.
- **Dependabot.** Its PR titles are `build(deps): ...`, which the title check accepts and the changelog check ignores. Its rebases keep the squash title. Nothing to configure.
- **`versionCodeOf` caps minor and patch at 99.** Many small releases could reach `0.100.0`. `release.sh apply` exit 5 makes that loud instead of failing at build time; the fix is a `feat!` to 1.0.0, or lifting the cap in `buildSrc` with a new test.
- **The serial coverage gate is slow and can misreport once** (see the memory note in the project's Claude memory and `ci.yml`'s comment). It stays a separate step in `pr.yml`; a rerun is the remedy, never a lowered floor.
- **Play's "What's new" is 500 characters.** The prose is the same text everywhere; the length warning is advisory because GitHub has no such limit.
- **The tag must be lowercase `vX.Y.Z`.** The build job validates it before checking anything out, as the Mac does.
- **No em dashes or en dashes** in workflow messages, scripts, changelog prose, or PR titles; the title check rejects the em dash byte sequence.

## Migration

Filled in as each step lands, in the ADR-033 style: PR link, merge date, and for step 6 the run links.

1. Branch CI and the hook:
2. PR CI and metadata; branch protection enabled:
3. Release script and tests:
4. Release workflow; release-please removed:
5. Docs and skills:
6. Proving cycle (runs and the release URL):

## Handoff

From here every change, including Claude's, is a branch and a PR with its own release note, and a release is one click that cannot ship without notes. Later phases' close-out is "push and open the PR", and their acceptance boxes can require the note. Nothing on `main` is ever unreviewed or untested, and `CHANGELOG.md` reads like it was written by a person, because it was.
