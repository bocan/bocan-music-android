# Phase 05: Library UI

> Depends on: phase-01-persistence.md, phase-04-playback-engine.md (phase-03's SyncBanner slot is used if present)
> Read docs/design-spec/_standards.md first.
> Provides: the app's navigation shell and every library browsing surface in `:app`.

## Goal

Open the app, see your library, find anything in seconds, start playback everywhere. Artists, Albums, Songs, Genres, Playlists, and Folders views over the synced DB; search; a persistent mini player bar; album and artist detail screens. Fast at 10,000 tracks.

## Non-goals

- No full Now Playing screen or queue sheet (phase 06).
- No podcast surfaces (phase 07).
- No editing of anything, anywhere. No context-menu items that imply writes (no "edit tags", no "delete file", no "add to playlist" since playlists are Mac-owned).

## Outcome shape

```
app/src/main/kotlin/io/cloudcauldron/bocan/app/
  navigation/{BocanNavHost.kt, Destinations.kt}
  home/{HomeScaffold.kt}                    // bottom nav: Library, Podcasts, Search, Settings
  library/{LibraryScreen.kt, LibraryTabRow.kt,
           AlbumsGrid.kt, ArtistsList.kt, SongsList.kt, GenresList.kt,
           PlaylistsScreen.kt, FoldersScreen.kt,
           AlbumDetailScreen.kt, ArtistDetailScreen.kt, PlaylistDetailScreen.kt,
           LibraryViewModel.kt, AlbumDetailViewModel.kt, ArtistDetailViewModel.kt}
  search/{SearchScreen.kt, SearchViewModel.kt}
  player/MiniPlayerBar.kt
  components/{TrackRow.kt, AlbumCell.kt, ArtworkImage.kt, EmptyState.kt, SortMenu.kt}
```

## Implementation plan

1. Navigation: Compose Navigation, four bottom destinations (Library, Podcasts placeholder, Search, Settings placeholder). Detail screens push onto the stack. Deep-link scheme `bocan://album/{id}` etc. (used by the widget later).
2. `HomeScaffold`: bottom bar + `MiniPlayerBar` docked above it (visible whenever a session item exists), edge-to-edge insets handled once here.
3. Library tabs (Artists, Albums, Songs, Genres, Playlists, Folders) as a scrollable secondary tab row, remembering the last selected tab.
   - Albums: adaptive `LazyVerticalGrid` of `AlbumCell` (artwork via Coil from `ArtworkStore`, title, artist, year), sort menu (title, artist, year, recently synced).
   - Artists: list with album counts; detail shows their albums grid + all-tracks section.
   - Songs: full track list with fast scroller (alphabet rail), sort menu (title, artist, album, duration).
   - Genres: list -> genre detail (albums grid + track list).
   - Playlists: tree honouring folders (`parentId`), accent color chip per playlist, detail screen lists tracks in `position` order with a "materialized from your Mac" subtitle for `smart` kind.
   - Folders: a browser over `relPath` segments (derived in the ViewModel by splitting paths; no filesystem walking).
4. `TrackRow`: artwork thumb, title, artist, duration, loved indicator (small heart when `loved`), rating shown as compact stars in detail contexts, download-state dimming for `pending` tracks (visible but marked "not synced yet"; tapping explains rather than plays). TalkBack merges the row into one sentence.
5. Tap behaviours: track tap plays the visible context (album, playlist, filtered list) from that index via `QueueController.playNow`. Long-press bottom sheet: Play next, Add to queue, Go to album, Go to artist. Album/playlist headers get Play and Shuffle buttons.
6. Search: FTS-backed, single query box, sectioned results (tracks, albums, artists, playlists), debounced 200 ms, keyboard-first. Recent searches (DataStore, last 10, clearable).
7. `MiniPlayerBar`: artwork, title/artist marquee, play/pause, progress hairline; tap opens Now Playing (route exists in phase 06; until then, no-op with TODO(phase-06)).
8. Empty states: not-yet-paired (button to pairing), paired-but-empty (button to Sync Now), and mid-first-sync (progress from `SyncState`).
9. Performance pass: stable keys on every lazy list, `contentType` set, artwork request sizes bounded, no reads off the main path (ViewModels expose `StateFlow` built from DAO Flows with `stateIn`).

## Definitions and contracts

- Every screen follows: `*Screen(state: StateFlow<UiState>, onEvent: (Event) -> Unit)` with a preview-friendly stateless inner composable.
- Sorting and tab selection persist via DataStore preferences.
- All strings via `strings.xml`; plurals via `<plurals>`; durations via `DateUtils.formatElapsedTime`.

## Context7 lookups

- use context7: Compose Navigation type-safe routes latest API
- use context7: LazyVerticalGrid performance stable keys contentType
- use context7: Coil 3 image request sizing and crossfade with local files

## Dependencies

Compose Navigation, Coil 3. Test: Robolectric + compose-ui-test for ViewModel-to-UI smoke; Turbine for ViewModel state.

## Test plan

- ViewModels: sort orders produce expected orderings from fixture DB; folder tree derivation from relPaths (nested, unicode, single-file roots); search debounce and sectioning (Turbine + test dispatcher).
- `TrackRow` semantics: merged content description "Title, Artist, Album, Duration".
- Navigation: album cell tap navigates with the right id; deep link resolves.
- Empty states: each of the three variants renders given the corresponding state.

## Acceptance criteria

- [ ] All six library tabs browse real synced data; detail screens play from context.
  - All six tabs, the album, artist, playlist, and genre detail screens, and play-from-context are implemented and the view model data plumbing is unit-tested, but browsing a real synced library and hearing playback needs a device.
- [x] Search returns sectioned results as you type and survives odd input.
- [ ] Mini player reflects the live session and controls play/pause.
  - The bar is built and wired to the session state and play/pause, but reflecting a live session needs playback running on a device.
- [ ] 10k-track Songs list scrolls at 60 fps on a mid-range device (no dropped-frame storms in a Perfetto trace; spot-check).
  - Stable keys and a single contentType are in place, but a 60 fps Perfetto trace needs a device.
- [x] Pending (unsynced) tracks are visibly distinct and unplayable with an explanation.
- [x] No write-implying actions anywhere in the UI.
- [x] TalkBack reads every row sensibly; every tappable has a label.
- [x] All copy in `strings.xml`; pseudolocale build shows no clipped hardcoded text.

## Gotchas

- **Do not build the folder tree in SQL.** Derive it from `relPath` in the ViewModel; the DB does not model directories.
- **Coil + thousands of thumbs**: set explicit sizes on requests or the grid decodes full-size art and scroll dies.
- **`stateIn` with `WhileSubscribed(5000)`** so rotation does not restart DB flows but backgrounding stops them.
- **Playlists are read-only**: resist the urge to add reordering drag handles; the Mac owns order.
- **Marquee text**: use `basicMarquee` only on the mini player, not in lists (animation cost per row).

## Handoff

Phase 06 assumes: `MiniPlayerBar` tap navigates to a `nowplaying` route, `HomeScaffold` hosts sheets, and long-press menus delegate queue ops to `QueueController`. Phase 07 assumes the Podcasts bottom destination placeholder exists.
