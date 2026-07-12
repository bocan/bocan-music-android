# Accessibility audit (phase 11)

A TalkBack-oriented walk of every screen, done by code review against the
standards charter (docs/design-spec/_standards.md, Accessibility). Each finding
below is either fixed in this phase or listed as an accepted deviation with its
reason. On-device TalkBack, font-scale, and switch-access passes remain manual
checks; everything testable in JVM tests is covered by
`app/src/test/.../components/SemanticsTests.kt` and `ComponentUiTests.kt`.

## Fixed in this phase

### Labels and merged semantics
- EpisodeRow: the info block (indicator, title, subtitle) now merges into one
  sentence "Title, date and duration, state"; the overflow menu stays its own
  target. The in-progress state is spoken (it was colour-only).
- MiniPlayerBar: the bar merges into "Now playing: Title, Artist. Opens the
  player." instead of reading fragments with no hint of what tapping does.
- Podcasts home: the continue-listening card reads "Resume Title"; a show cell
  reads "Title, Author, N unplayed episodes" (plurals), instead of a bare badge
  number.
- Now Playing: the star rating row merges into "Rated N of 5"; it was entirely
  invisible to TalkBack.
- Search: section headers are marked as headings, matching the EQ screen, so
  heading navigation works across both.

### State exposure
- Shuffle exposes On/Off via stateDescription (tint alone carried it before)
  and its touch target grew from 28 dp to 48 dp.
- Repeat exposes "Repeat off / Repeating all / Repeating this track".
- Every bare Switch (EQ master, skip silence, scrobble master, per-provider)
  became a whole-row Switch-role target, so the label, summary, and state read
  as one sentence and the target is the row, not the 32 dp thumb. New settings
  screens use the shared SettingsToggleRow, which does this by construction.
- The queue's current row and the chapters sheet's active chapter expose a
  "Now playing" / "Playing now" state; both were colour-only.

### Sliders
- SeekBar announces "Seek" with "M:SS of M:SS" instead of a bare percentage.
- EQ band and effect sliders already read textual values ("1 kilohertz, plus
  3.0 decibels"); now locked by a semantics test.

### Touch targets
- Queue drag handle: 28 dp to 48 dp.
- EQ preset delete: ~24 dp to the minimum interactive size.
- Shuffle: 28 dp to 48 dp (above).

### Inaccessible actions
- Queue rows expose Move up, Move down, and Remove from queue as custom
  accessibility actions; long-press-drag and swipe-to-dismiss are invisible to
  accessibility services.
- The lyrics timing-offset chip was a focusable button with a no-op click (a
  dead stop in traversal); it is now plain text.

### Live announcements
- Sync progress (banner and sync settings) announces phase changes politely,
  and only phases: the announced text never carries per-file counts, per the
  guidance against live regions on rapidly-updating content.
- The sleep timer announces arming and fading once per state change; the
  countdown itself stays quiet.
- The playback speed value announces changes from the plus and minus buttons.
- The pairing code-mismatch error announces assertively when it appears.

### Font scale and clipping
- The albums shelf in artist/genre detail dropped its fixed 220 dp height, so
  two-line cells no longer clip at 200 percent font scale.
- The four detail-screen top bar titles now ellipsize instead of clipping.
- All typography is sp-based Material styles (no dp font sizes anywhere).

### Reduced motion
- The mini player marquee no longer scrolls under reduced motion. Lyrics
  auto-scroll and the ambient background already honoured it (phase 06).

## Accepted deviations

- The alphabet fast-scroll rail in Songs stays hidden from accessibility
  services and its letter targets are under 48 dp. It is a redundant shortcut:
  the list itself scrolls normally, so TalkBack and switch users lose nothing.
  Exposing 26 tiny targets would make traversal worse, not better.
- Coil crossfades (artwork fade-in) are not gated on reduced motion; they are
  single 200 ms content transitions, not continuous motion.
- High-contrast mode has no dedicated colour scheme; dark and pure-black themes
  use contrast-checked Material tokens throughout. Revisit if a user report
  shows a concrete gap.

## Remaining manual checks (need a device)

- A full TalkBack traversal of every screen, focus order, and no-trap check.
- Font scale 200 percent visual pass on Now Playing, rows, and settings.
- Switch-access pass over the queue custom actions.
