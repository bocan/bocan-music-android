# Phase 11: Polish, Settings, Accessibility, Localization

> Depends on: phases 02 through 10 (this phase audits and completes them)
> Read docs/design-spec/_standards.md first.
> Provides: the unified settings surface, onboarding, a TalkBack and theming audit, and localization scaffolding.

## Goal

Turn a working app into a finished one: a coherent Settings hub, a first-run onboarding that gets a new user paired and synced, a full accessibility pass, Material You consistency in both themes, and the localization pipeline proven with a pseudolocale.

## Non-goals

- No new features. Anything discovered here that is a feature goes to the backlog, not this phase.
- No translations beyond English; only the machinery that makes them possible.

## Outcome shape

```
app/src/main/kotlin/io/cloudcauldron/bocan/app/settings/
  SettingsScreen.kt            // hub
  sections/{SyncSettings.kt, PlaybackSettings.kt, PodcastSettings.kt,
            ScrobbleSettingsScreen.kt (exists), AppearanceSettings.kt, AboutScreen.kt}
app/src/main/kotlin/io/cloudcauldron/bocan/app/onboarding/
  OnboardingFlow.kt            // welcome -> pair -> first sync -> done
docs/accessibility-audit.md
docs/theming-audit.md
```

## Implementation plan

1. **Settings hub** with sections:
   - Sync: paired Mac (name, fingerprint tail, unpair), auto-sync toggles (on discovery, periodic, charging-only), storage usage + "remove all synced media" (with a scary confirm; this deletes phone copies only and says so).
   - Playback: ReplayGain mode/preamp link to EQ screen, fades, skip silence defaults, resume behaviour.
   - Podcasts: default speed, skip intervals.
   - Scrobbling: existing screen, linked.
   - Appearance: theme (system/light/dark), dynamic color toggle (falls back to Bòcan brand palette), pure-black dark option for OLED.
   - About: version, licenses (Google OSS licenses plugin or a static generated list), link to the Bòcan site, "what syncs and what never leaves this phone" privacy explainer.
2. **Onboarding**: first launch detects unpaired state: three screens (what Bòcan is; pair with your Mac, embedding the phase 02 flow; first sync progress with cancel-and-do-later). Skippable; re-enterable from Settings.
3. **Accessibility audit** (write `docs/accessibility-audit.md` with findings and fixes):
   - TalkBack walk of every screen: labels, merged semantics, state announcements (play/pause, shuffle on), slider value text, focus order, no traps.
   - Touch targets at least 48 dp; text contrast at least 4.5:1 in both themes (check the dimmed pending-track rows especially).
   - Font scale 200 percent: no clipped or overlapping text on Now Playing, rows, settings.
   - Reduced motion: lyrics auto-scroll, marquee, ambient background all degrade per phase 06.
4. **Theming audit** (`docs/theming-audit.md`): both themes plus dynamic-color on/off matrix over every screen; fix hardcoded colors (rule: only theme tokens in composables); verify the accent fallbacks match the brand palette from phase 00.
5. **Localization proof**: enable `pseudoLocalesEnabled`, run the app in `en-XA`, fix truncation and concatenation bugs; add a lint/detekt guard that fails on string concatenation building user-visible sentences; verify plurals use `<plurals>`.
6. **Error surface sweep**: every typed error that can reach the UI has a human string (map `SyncError`/`PlaybackError` cases to string resources; no `toString` leaks to users).
7. **Performance re-check** against the `_standards.md` baselines; record numbers in the PR.

## Context7 lookups

- use context7: Compose semantics merging stateDescription and live region announcements
- use context7: Android per-app language preferences and pseudolocale testing

## Dependencies

OSS licenses tooling only.

## Test plan

- Semantics tests for the highest-traffic components (TrackRow, transport, EQ sliders, episode rows): labels and state descriptions exact.
- Error-mapping test: every sealed error case has a non-empty string resource (exhaustive `when`, compile-time enforced).
- Onboarding state machine: fresh install -> onboarding; paired install -> straight to library; skip path re-enterable.
- Screenshot-style checks optional; if added, use Compose previews + Paparazzi, both themes, and commit the golden set.

## Acceptance criteria

- [x] Settings hub complete; every toggle wired to real behaviour; unpair and remove-media flows confirmed and working.
- [ ] Onboarding takes a fresh install to playing music without touching anything else.
  - The flow, its latch, and the pair-then-first-sync embedding are built and unit tested (fresh install to onboarding, paired install to library, skip re-enterable); walking a real fresh install through to audible music needs a device and a paired Mac.
- [x] TalkBack audit doc committed with zero open blockers; all fixes landed.
- [x] Both themes clean across the matrix; no hardcoded colors remain in composables.
- [ ] en-XA run shows no truncation/concatenation defects; guards in CI.
  - The CI guards (UserVisibleStringConcatenation, BareTextLiteral) are live in detekt, every code-level concatenation and missing plural from the audit is fixed, and pseudoLocalesEnabled is on for debug; the visual en-XA truncation pass needs a device or emulator.
- [x] Every user-reachable error renders a helpful localized message.
- [ ] Performance baselines re-verified and recorded.
  - Code-level re-check: list rows keep stable keys and single contentTypes, the new settings and onboarding screens introduce no per-frame work, storage sizing runs only on terminal sync states, and nothing new holds a wakelock. Cold launch, the 10k-track Perfetto trace, and seek latency need a mid-range device.

## Gotchas

- **"Remove all synced media" must not unpair** and must leave local state tables intact (a re-sync restores the library and keeps play history). Test this; users will use it to free space.
- **Dynamic color can destroy contrast** with certain wallpapers; the audit must check dynamic themes with a low-contrast seed, and the pure-black option must keep primary readable.
- **Onboarding embeds pairing; do not fork it.** One pairing implementation with two entry points, or the flows will drift.

## Handoff

Phase 12 assumes the app is feature-complete and audited; its job is shipping, not fixing.
