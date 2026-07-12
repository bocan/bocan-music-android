# Theming audit (phase 11)

Both themes, dynamic color on and off, and the new pure-black option were
audited over every screen by code review. The rule enforced: composables use
MaterialTheme tokens only; the sole exceptions are data-driven colors (playlist
accents, artwork-derived washes), which must be contrast-guarded.

## The matrix

Screens audited: home scaffold, library (six tabs), album / artist / genre /
playlist detail, folders, search, now playing (with queue, lyrics, sleep timer,
speed sheets), podcasts home, show detail (show notes, chapters), equalizer,
pairing, onboarding, settings hub and every section (sync, playback, podcasts,
scrobbling, appearance, about), mini player, sync banner, and the Glance
widget. Modes: light and dark, each with dynamic color on and off (API 31+),
plus pure-black dark.

Theme decisions live only in theme/Theme.kt: `isSystemInDarkTheme` appears
nowhere else, and appearance preferences (mode, dynamic, pure black) flow in
from one place. The pre-Compose window background (values/themes.xml and
values-night/themes.xml) matches the Compose LightSurface/NightSurface tokens
exactly, so launch does not flash a mismatched frame.

## Fixed in this phase

- Glance widget subtitle used a hardcoded mid-gray (0xFFB0B0B0) that ignored
  day/night; it now uses GlanceTheme onSurfaceVariant. The widget's control
  icons (platform media drawables, fixed light gray) are now tinted with
  GlanceTheme onSurface so they are visible on a light widget background.
- Show notes rendered links with the legacy platform accent (the TextView
  default); links now take the theme's primary alongside the already-themed
  text color.
- Pending track rows dimmed the whole row to 45 percent alpha, dropping both
  text lines below the 4.5:1 contrast floor. Rows now dim the artwork only and
  shift the title to onSurfaceVariant at full opacity.
- Played episode rows had the same defect at 55 percent alpha, plus an
  unplayed indicator at 25 percent primary (below the 3:1 non-text floor).
  Text stays at full-opacity tokens, the unplayed ring uses the outline token,
  and the played check sits at 80 percent primary (a 20 dp non-text icon
  adjacent to full-contrast text).
- The Now Playing ambient wash could push text below its contrast floor with
  a bright artwork palette at 55 percent alpha; the wash is capped at 30
  percent, which keeps onSurface and onSurfaceVariant text above 4.5:1 over
  any wash color in both themes.
- Playlist accent colors come from synced data and could be any hex; the
  folder icon now falls back to the primary token whenever the accent would
  sit below 3:1 against the list surface (WCAG relative-luminance check in
  PlaylistsScreen).

## Verified clean

- No other `Color(0x...)`, named color, or hex parsing exists outside
  theme/Color.kt (the brand token source) and the two guarded data-driven
  sites above.
- Dynamic color falls back to the Bocan brand palette (AccentLight/AccentDark
  seeds) below API 31 and when the toggle is off; the accent matches the
  phase 00 brand palette.
- Pure black replaces backgrounds and the lowest surface containers with true
  black while keeping every content and accent role from the base scheme, so
  primary stays readable; covered by ThemeResolutionTests.
- Scrims and gradients: the only gradient is the ambient wash (capped above);
  DetailHeader draws no text over artwork.

## Remaining manual checks (need a device)

- Dynamic-color themes seeded from a low-contrast wallpaper, light and dark.
- Pure-black visual pass on an OLED panel.
