# Phase 15: Android Auto That Can Find Things

> Depends on: phase-05-library-ui.md (the FTS search), phase-07-podcasts.md, phase-10-system-integration.md (the browse tree this phase reshapes)
> Read docs/design-spec/_standards.md first. No protocol change: everything here is browse-tree and session work in `:core:playback`, with labels and wiring in `:app`.
> Provides: a car browse tree with no silent cap, letter grouping for long lists, search from the car's search bar and from voice, root tabs, grid style for covers, and a Recently Played node.

## Goal

In the car, a song or an artist is at most three taps or one sentence away. Today the artists list stops at the 200th name because the tree clamps every list to 200 rows and Android Auto never asks for a second page; there is no search at all, so the car's search bar and "play Slowdive on Bòcan" do nothing; six root folders render as a plain list instead of tabs; and every album is a text row. This phase makes the tree shaped for a driver: short lists reached through letters, search that returns artists, albums, songs, and shows, four tabs, covers in a grid, and the things you played last at the top.

## Non-goals

- No custom car UI. Android Auto media apps use the platform templates (list, grid, tabs, search, player) for driver distraction reasons; this phase works inside them.
- No change to the phone UI, the sync protocol, or the database schema beyond new read queries.
- No Android Automotive OS (cars running Android natively) work; the same tree serves it if it ever comes, but nothing here targets it.
- No assistant "play some jazz" mood parsing. Voice queries are matched against the library's own titles, artists, albums, and shows.

## Outcome shape

```
core/playback/src/main/kotlin/io/cloudcauldron/bocan/playback/browse/
  MediaTree.kt                 // reshaped: root tabs, letter buckets, Recently Played, style hints, no silent cap
  BrowseStyle.kt               // the content-style extras (list, grid, group title) in one place
  LetterBuckets.kt             // pure: name -> bucket key ("A".."Z", "0-9", "#"), bucket ordering, and grouping
  BrowseSearch.kt              // FTS query -> ranked MediaItems (artists, albums, songs, shows)
core/playback/src/main/kotlin/io/cloudcauldron/bocan/playback/PlaybackService.kt
                               // root children limit per controller, onSearch, onGetSearchResult, onSetMediaItems for voice
core/persistence/src/main/kotlin/io/cloudcauldron/bocan/persistence/daos/BrowseDao.kt
                               // (id, name) projections for bucketing, members by id, recently played, unbounded pages
core/persistence/src/main/kotlin/io/cloudcauldron/bocan/persistence/daos/SearchDao.kt
                               // one-shot (suspend) search for the session, beside the Flow one; shows by escaped LIKE
app/src/main/kotlin/io/cloudcauldron/bocan/app/AppGraph.kt          // labels, BrowseSearch wiring
app/src/main/res/values/strings.xml                                  // Recently Played, letter bucket labels
core/playback/src/test/kotlin/io/cloudcauldron/bocan/playback/browse/
  MediaTreeTests.kt  LetterBucketsTests.kt  BrowseSearchTests.kt  BrowseStyleTests.kt
core/persistence/src/test/kotlin/io/cloudcauldron/bocan/persistence/daos/BrowseDaoTests.kt
docs/release-checklist.md                                            // DHU steps for this phase
```

## What carries over from previous specs

- `MediaTree` (phase 10) is the whole car surface: root categories, paged children by `LIMIT`/`OFFSET`, browsable folders and playable items built from Room rows. Its `MAX_PAGE = 200` clamp is the bug this phase removes. Its tests against the fixture database are the pattern for the new ones.
- `BrowseDao` (phase 10) serves every level by index. New queries follow its `LIMIT :limit OFFSET :offset` shape.
- `SearchDao` and `ftsMatchExpression` (phase 05) already turn raw text into a safe FTS5 `MATCH` and return tracks, albums, and artists. The car reuses the same sanitiser; a suspend variant is added because the session wants one result, not a Flow.
- `MediaId` and `onAddMediaItems` (phase 10): browse items carry only an id; the session resolves the playable item. Search results and voice matches use the same ids, so nothing new is needed to play them.
- `PlayStatsDao.lastPlayedAt` (phase 06) is what Recently Played orders by.
- `ArtworkReadGrants` (phase 10) makes cover Uris readable in the car; grid style relies on it.

## Implementation plan

1. **Remove the silent cap.** Android Auto subscribes to a node once with no page options, and Media3 passes that through as page 0 with an unbounded page size. `MediaTree.children` must serve the whole node in that case. Replace `MAX_PAGE` with two limits: an explicit page size from a paging browser is honoured up to 500; an unbounded request is served whole, up to `CAR_LIST_CAP` (400) rows, which no node reaches once step 2 lands. Keep `LIMIT`/`OFFSET` in the DAO.
2. **Letter buckets for artists and albums.** `Artists` and `Albums` open to bucket folders: `A` to `Z`, `0-9`, and `#` for everything else, in that order, showing only buckets that have members. Each bucket opens to its members, alphabetical. `LetterBuckets.keyFor(name)` is a pure function: strip a leading "The " or "A " when a following word exists, take the first letter, fold accents (NFD, drop combining marks), upper-case; digits go to `0-9`; anything not A to Z after folding goes to `#`. **Chosen route (2026-11-05): group in Kotlin.** SQLite cannot fold accents (`COLLATE NOCASE` is ASCII only), and a stored `sortKey` would be the first Room migration. Instead `BrowseDao` serves a light `(id, name)` projection of every artist or album, `LetterBuckets.group` buckets it with `keyFor`, and the members of one bucket are read back by id. `keyFor` stays the single definition of a bucket; 2,000 names group in a few milliseconds. No schema change.
3. **Search.** `BrowseSearch.search(query, limit)` runs the sanitised FTS match once and returns one flat list in sections: artists (as browsable folders), albums (browsable), songs (playable), shows (browsable), each section headed with a group-title hint so the car renders headers. Rank: exact title matches first, then prefix, then the FTS order; cap each section at 10. Shows are not in `tracks_fts`; they match by an escaped `LIKE '%query%'` on `podcasts.title` (a small table). Wire `onSearch` (acknowledge, then notify the browser with the result count) and `onGetSearchResult` in the service. Search support is advertised by Media3 itself: in 1.9.0 the legacy browser stub writes `android.media.browse.SEARCH_SUPPORTED` into the root extras whenever the controller has `COMMAND_CODE_LIBRARY_SEARCH` available, so the session must keep that command in its available set, and a test pins it. No hand-written extra.
4. **Voice.** "Play X on Bòcan" reaches the session as `onSetMediaItems` with a media item whose request metadata carries the search query. Resolve it with `BrowseSearch`: the first artist match plays that artist's tracks, else the first album, else the first song, else the first show's latest episode. An empty query ("play Bòcan") plays Recently Played, and if that is empty, shuffles everything downloaded. When nothing matches, `resolveVoice` returns an empty list and the service fails the request with `PlaybackError.VoiceNoMatch`, so the current queue is left alone. (Amended 2026-11-05: Media3 1.9.0 applies a successful empty result with `setMediaItems`, which would wipe the listener's queue; a failed result changes nothing.)
5. **Root tabs and Recently Played.** The root becomes four nodes in this order: `Home`, `Playlists`, `Albums`, `Artists`. `Home` holds `Recently Played` (tracks by `lastPlayedAt` desc, 50), `Continue Listening` (episodes, as today), `Podcasts` (shows), and `Songs` (recently synced, as today). Read the browser's root children limit hint; when the car reports fewer than four, collapse to `Home`, `Playlists`, `Albums`. The hint (`MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT`) arrives only in `onGetLibraryRoot`'s params, so the service remembers it per controller and passes it to `children` when that controller asks for the root's children.
6. **Style hints.** `BrowseStyle` builds the extras bundle: albums and shows render as a grid, everything else as a list; playable children of an album render as a list. Group titles on search sections. Every browsable and playable item passes through it so the choice is in one place.
7. **Labels.** All new titles (`Home`, `Recently Played`, bucket names, search section headers) come from `strings.xml` through `BrowseLabels`, as the existing six do. The search section headers reuse the category titles (`Artists`, `Albums`, `Songs`, `Podcasts`); the letters `A` to `Z` are data, and only the `0-9` and `#` bucket titles are strings.
8. **Docs.** Add the DHU steps to `docs/release-checklist.md`; tick the phase 10 Auto box only when this phase's checklist passes on the head unit.

## Definitions and contracts

```kotlin
object LetterBuckets {
    val ORDER: List<String>                       // "A".."Z", "0-9", "#"
    fun keyFor(name: String): String              // pure; see step 2
    fun <T> group(rows: List<T>, name: (T) -> String): Map<String, List<T>>   // non-empty buckets in ORDER, members alphabetical
}

class BrowseStyle(private val artworkUri: (String?) -> Uri?) {
    fun folder(mediaId: String, title: String, artworkHash: String?, grid: Boolean, group: String? = null): MediaItem
    fun playable(mediaId: String, title: String, subtitle: String?, artworkHash: String?, durationMs: Long, isPodcast: Boolean, group: String? = null): MediaItem
}

class BrowseSearch(private val searchDao: SearchDao, private val browseDao: BrowseDao, private val style: BrowseStyle, private val labels: BrowseLabels, private val dispatchers: CoroutineDispatchers) {
    suspend fun search(query: String, perSection: Int = 10): List<MediaItem>
    suspend fun resolveVoice(query: String): List<MediaId>   // the ids to play, in order; empty when nothing matches
}

class MediaTree(...) {
    fun rootItem(): MediaItem
    fun rootCategories(rootChildrenLimit: Int?): List<MediaItem>
    suspend fun children(parentId: String, page: Int, pageSize: Int, rootChildrenLimit: Int? = null): List<MediaItem>
    companion object {
        const val CAR_LIST_CAP = 400
        const val CATEGORY_HOME = "bocan/home"
        const val CATEGORY_RECENT = "bocan/recent"
        const val PREFIX_ARTIST_BUCKET = "bocan/artists/"    // + bucket key
        const val PREFIX_ALBUM_BUCKET = "bocan/albums/"
    }
}
```

Rules:

- **No node may return a truncated list without paging.** If a node's true size exceeds `CAR_LIST_CAP`, that is a design bug in the tree shape, not something to hide. Log a warning with the node id and size. Two nodes are bounded by definition and never warn: Recently Played (the newest 50 plays) and Songs (the newest `CAR_LIST_CAP` tracks by sync time, which is what "recently synced" means for a library of 10,000 tracks).
- **Ids stay opaque and stable.** Bucket ids embed the bucket key (`bocan/artists/S`), never a row offset, so a car that caches ids across syncs still lands somewhere sensible.
- **Search never throws.** A query that sanitises to nothing returns an empty list.
- **Voice resolution plays through the normal `MediaId` path**, so play stats, scrobbling, and the demo-id skip all behave as for a tap.
- **Style hints are additive extras.** A browser that ignores them (the phone's own controller) sees the same tree as before.

## Context7 lookups

- use context7: Media3 MediaLibrarySession.Callback onSearch onGetSearchResult notifySearchResultChanged
- use context7: Media3 onSetMediaItems requestMetadata searchQuery voice playFromSearch
- use context7: Media3 MediaConstants EXTRAS_KEY_CONTENT_STYLE_BROWSABLE EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE EXTRAS_KEY_ROOT_CHILDREN_LIMIT
- use context7: Android Auto media app root hints search supported extra
- use context7: Media3 legacy browser onLoadChildren without options page size passed to onGetChildren
- use context7: SQLite unicode accent folding in Room queries

Verify the constant names against the Media3 version in the catalog (1.9.0, pinned for the FFmpeg decoder) before writing code; the legacy `androidx.media.utils.MediaConstants` names differ from the Media3 ones and both must resolve to the same string keys.

## Dependencies

No new Gradle dependencies. Manual testing needs the Desktop Head Unit (DHU); the release checklist has the sideloading note for making a debug build visible to Auto.

## Test plan

- `LetterBucketsTests` (pure): "The Beatles" to `B`, "A Tribe Called Quest" to `T`, "A" alone to `A`, "Édith Piaf" to `E`, "2Pac" to `0-9`, "…And You Will Know Us" to `#`, empty string to `#`; `ORDER` is A to Z then `0-9` then `#`.
- `LetterBucketsTests` also covers `group`: only non-empty buckets, in `ORDER`, members alphabetical.
- `BrowseDaoTests` (Robolectric, fixture manifest): the `(id, name)` projections return every artist and album; members by id come back alphabetically; recently played orders by `lastPlayedAt` desc, skips never-played rows, and skips tracks not downloaded; an unbounded page returns every row.
- `MediaTreeTests` (existing file, extended; a hand-built library of 30 artists across 6 buckets): root yields four tabs, or three under a limit of 3; `Artists` yields only non-empty bucket folders in order, not artists; a bucket yields its artists alphabetically; `children` with an unbounded page size returns the whole node; a node over `CAR_LIST_CAP` logs a warning and returns the cap; albums carry the grid hint and artists the list hint; ids are stable across two calls.
- `BrowseSearchTests`: "slow" finds Slowdive under artists and "Souvlaki Space Station" under songs, sections in order with group titles; an exact title outranks a prefix; each section caps at `perSection`; a query of punctuation only returns empty; `resolveVoice("slowdive")` yields that artist's track ids in album order; `resolveVoice("")` yields recently played, or a shuffle of downloaded tracks when there is none.
- `PlaybackServiceTests`: a connecting controller has `COMMAND_CODE_LIBRARY_SEARCH` available (what makes Media3 advertise search); `onSearch` then `onGetSearchResult` round-trips through a fake tree.
- Manual, on the DHU: tabs render; Albums is a grid of covers; Artists shows letters, a letter shows its artists, tapping one plays; the search bar returns sectioned results; voice "play <an artist in the library> on Bòcan" starts playback; "play Bòcan" plays Recently Played; a library of 2,000 artists shows every letter with no missing names.

## Acceptance criteria

- [ ] Every artist and album in a 2,000-artist library is reachable in the car, none missing.
      Device check (DHU), not done. Unit evidence only: `MediaTreeTests` shows every artist of a 30-artist library lands in exactly one bucket and a bucket serves all its members.
- [x] Artists and Albums open to letters; no car list exceeds `CAR_LIST_CAP`.
      Evidence: `MediaTreeTests` "artists open to only the non empty buckets in order", "albums open to buckets whose albums are a grid of covers", "a node over the car cap logs a warning and returns the cap" (commit `ab71599`).
- [ ] The car's search bar returns artists, albums, songs, and shows in labelled sections.
      Device check (DHU), not done. Unit evidence only: `BrowseSearchTests` (sections, group titles, ranking, cap) and `PlaybackServiceTests` (the library search command that makes Media3 advertise search).
- [ ] Voice "play <artist> on Bòcan" plays that artist; "play Bòcan" plays Recently Played.
      Device check (DHU), not done. Unit evidence only: `BrowseSearchTests` voice cases and `voiceQuery`.
- [ ] The root renders as four tabs on the DHU; Albums and Podcasts render as grids with covers.
      Device check (DHU), not done. Unit evidence only: `MediaTreeTests` root and style-hint cases, `BrowseStyleTests`.
- [x] Recently Played lists the last 50 played tracks, newest first.
      Evidence: `BrowseDaoTests` "recently played orders newest first and skips never played and not downloaded" (real Room database) and `MediaTreeTests` "recently played is the newest fifty plays".
- [x] Phone browsing and playback are unchanged (the phone never reads this tree).
      Evidence: the branch changes no phone screen or view model; in `:app` it touches only `AppGraph` car wiring, new strings, and one new `ErrorMessages` branch. The moved `downloadedTrackIds` query is pinned for both DAOs in `BrowseDaoTests`; all 578 unit tests pass.
- [x] `./gradlew check test assembleDebug` and the serial `koverVerify` green; `/android-standards` clean; `/protocol-guard` reports no contract impact.
      Evidence: on `a7aa924`, `check test assembleDebug` exit 0 and `koverVerify --no-parallel --rerun-tasks` exit 0 with 578 tests and 0 failures; the standards review's one should-fix landed as `a7aa924`; protocol-guard classified every change as implementation-only with no wire files touched. CI not yet run.

## Gotchas

- **The 200 cap is the whole bug for artists.** Do not "fix" it by raising the number; the cap only exists because a flat list of every artist is the wrong shape for a car. Buckets first, then the cap becomes a safety net.
- **Media3 delivers a non-paging browser as page size `Int.MAX_VALUE`.** Any `coerceIn` on the page size silently truncates. Treat "unbounded" as a distinct case.
- **Search must be advertised or the car hides the bar.** Implementing the callbacks is not enough; the root extra is what the head unit reads. Media3 1.9.0 writes it from the available session commands, so removing `COMMAND_CODE_LIBRARY_SEARCH` from `onConnect` silently hides the bar.
- **Voice queries arrive lower-cased and stripped.** Match through the FTS sanitiser, never by string equality on the raw query.
- **Group titles need the whole result list in one response.** Sections are contiguous runs of items with the same group title; do not page search results.
- **Accent folding in SQLite is not free.** `COLLATE NOCASE` is ASCII only. Step 2 folds in Kotlin for that reason; test "Édith" explicitly.
- **Artwork in a grid is loaded by the car's process.** The read grant on connect (phase 10) already covers it; a blank grid means the grant failed, not the style hint.
- **No em dashes or en dashes** in labels, bucket names, or search headers.

## Handoff

The car tree is now a first-class surface with its own search. Later phases can add Auto-only extras (download or explicit badges, progress on episodes) through `BrowseStyle` without touching the tree shape, and Android Automotive OS would reuse the same tree unchanged.
