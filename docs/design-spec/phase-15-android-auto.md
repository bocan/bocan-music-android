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
  LetterBuckets.kt             // pure: name -> bucket key ("A".."Z", "0-9", "#"), and bucket ordering
  BrowseSearch.kt              // FTS query -> ranked MediaItems (artists, albums, songs, shows)
core/playback/src/main/kotlin/io/cloudcauldron/bocan/playback/PlaybackService.kt
                               // onGetLibraryRoot params, onSearch, onGetSearchResult, onSetMediaItems for voice
core/persistence/src/main/kotlin/io/cloudcauldron/bocan/persistence/daos/BrowseDao.kt
                               // bucket queries, recently played, unbounded pages for the car
core/persistence/src/main/kotlin/io/cloudcauldron/bocan/persistence/daos/SearchDao.kt
                               // one-shot (suspend) search for the session, beside the Flow one
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
2. **Letter buckets for artists and albums.** `Artists` and `Albums` open to bucket folders: `A` to `Z`, `0-9`, and `#` for everything else, in that order, showing only buckets that have members. Each bucket opens to its members, alphabetical. `LetterBuckets.keyFor(name)` is a pure function: strip a leading "The " or "A " when a following word exists, take the first letter, fold accents (NFD, drop combining marks), upper-case; digits go to `0-9`; anything not A to Z after folding goes to `#`. The DAO does the grouping in SQL with a `bucket` column derived at query time: add `SELECT DISTINCT` bucket counts and `WHERE bucket = :key` page queries. If SQLite string functions cannot express the accent fold, store a `sortKey` column on artists and albums computed by the applier (a schema migration, in which case phase 01's migration test pattern applies); prefer the SQL-only route and record which was chosen.
3. **Search.** `BrowseSearch.search(query, limit)` runs the sanitised FTS match once and returns one flat list in sections: artists (as browsable folders), albums (browsable), songs (playable), shows (browsable), each section headed with a group-title hint so the car renders headers. Rank: exact title matches first, then prefix, then the FTS order; cap each section at 10. Wire `onSearch` (acknowledge, then notify the browser with the result count) and `onGetSearchResult` in the service. Advertise search support in the root's `LibraryParams` extras so the car shows its search bar.
4. **Voice.** "Play X on Bòcan" reaches the session as `onSetMediaItems` with a media item whose request metadata carries the search query. Resolve it with `BrowseSearch`: the first artist match plays that artist's tracks, else the first album, else the first song, else the first show's latest episode. An empty query ("play Bòcan") plays Recently Played, and if that is empty, shuffles everything downloaded. No match returns an empty list, which the car reports as "not found".
5. **Root tabs and Recently Played.** The root becomes four nodes in this order: `Home`, `Playlists`, `Albums`, `Artists`. `Home` holds `Recently Played` (tracks by `lastPlayedAt` desc, 50), `Continue Listening` (episodes, as today), `Podcasts` (shows), and `Songs` (recently synced, as today). Read the browser's root children limit hint; when the car reports fewer than four, collapse to `Home`, `Playlists`, `Albums`.
6. **Style hints.** `BrowseStyle` builds the extras bundle: albums and shows render as a grid, everything else as a list; playable children of an album render as a list. Group titles on search sections. Every browsable and playable item passes through it so the choice is in one place.
7. **Labels.** All new titles (`Home`, `Recently Played`, bucket names, search section headers) come from `strings.xml` through `BrowseLabels`, as the existing six do.
8. **Docs.** Add the DHU steps to `docs/release-checklist.md`; tick the phase 10 Auto box only when this phase's checklist passes on the head unit.

## Definitions and contracts

```kotlin
object LetterBuckets {
    val ORDER: List<String>                       // "A".."Z", "0-9", "#"
    fun keyFor(name: String): String              // pure; see step 2
}

class BrowseStyle {
    fun folder(mediaId: String, title: String, artworkHash: String?, grid: Boolean, group: String? = null): MediaItem
    fun playable(mediaId: String, title: String, subtitle: String?, artworkHash: String?, durationMs: Long, isPodcast: Boolean, group: String? = null): MediaItem
}

class BrowseSearch(private val searchDao: SearchDao, private val podcastDao: PodcastDao, private val style: BrowseStyle, private val labels: BrowseLabels, private val dispatchers: CoroutineDispatchers) {
    suspend fun search(query: String, perSection: Int = 10): List<MediaItem>
    suspend fun resolveVoice(query: String): List<MediaId>   // the ids to play, in order; empty when nothing matches
}

class MediaTree(...) {
    fun rootItem(): MediaItem
    fun rootCategories(rootChildrenLimit: Int?): List<MediaItem>
    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem>
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

- **No node may return a truncated list without paging.** If a node's true size exceeds `CAR_LIST_CAP`, that is a design bug in the tree shape, not something to hide. Log a warning with the node id and size.
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
- `BrowseDaoTests` (Robolectric, fixture manifest plus a hand-built one with 30 artists across 6 buckets): bucket counts list only non-empty buckets in order; a bucket page returns its members alphabetically; recently played orders by `lastPlayedAt` desc and skips never-played rows; an unbounded page returns every row.
- `MediaTreeTests` (existing file, extended): root yields four tabs, or three under a limit of 3; `Artists` yields bucket folders not artists; a bucket yields its artists; `children` with an unbounded page size returns the whole node; a node over `CAR_LIST_CAP` logs a warning and returns the cap; albums carry the grid hint and artists the list hint; ids are stable across two calls.
- `BrowseSearchTests`: "slow" finds Slowdive under artists and "Souvlaki Space Station" under songs, sections in order with group titles; an exact title outranks a prefix; each section caps at `perSection`; a query of punctuation only returns empty; `resolveVoice("slowdive")` yields that artist's track ids in album order; `resolveVoice("")` yields recently played, or a shuffle of downloaded tracks when there is none.
- `PlaybackServiceTests`: `onGetLibraryRoot` params advertise search; `onSearch` then `onGetSearchResult` round-trips through a fake tree.
- Manual, on the DHU: tabs render; Albums is a grid of covers; Artists shows letters, a letter shows its artists, tapping one plays; the search bar returns sectioned results; voice "play <an artist in the library> on Bòcan" starts playback; "play Bòcan" plays Recently Played; a library of 2,000 artists shows every letter with no missing names.

## Acceptance criteria

- [ ] Every artist and album in a 2,000-artist library is reachable in the car, none missing.
- [ ] Artists and Albums open to letters; no car list exceeds `CAR_LIST_CAP`.
- [ ] The car's search bar returns artists, albums, songs, and shows in labelled sections.
- [ ] Voice "play <artist> on Bòcan" plays that artist; "play Bòcan" plays Recently Played.
- [ ] The root renders as four tabs on the DHU; Albums and Podcasts render as grids with covers.
- [ ] Recently Played lists the last 50 played tracks, newest first.
- [ ] Phone browsing and playback are unchanged (the phone never reads this tree).
- [ ] `./gradlew check test assembleDebug` and the serial `koverVerify` green; `/android-standards` clean; `/protocol-guard` reports no contract impact.

## Gotchas

- **The 200 cap is the whole bug for artists.** Do not "fix" it by raising the number; the cap only exists because a flat list of every artist is the wrong shape for a car. Buckets first, then the cap becomes a safety net.
- **Media3 delivers a non-paging browser as page size `Int.MAX_VALUE`.** Any `coerceIn` on the page size silently truncates. Treat "unbounded" as a distinct case.
- **Search must be advertised or the car hides the bar.** Implementing the callbacks is not enough; the root params extra is what the head unit reads.
- **Voice queries arrive lower-cased and stripped.** Match through the FTS sanitiser, never by string equality on the raw query.
- **Group titles need the whole result list in one response.** Sections are contiguous runs of items with the same group title; do not page search results.
- **Accent folding in SQLite is not free.** `COLLATE NOCASE` is ASCII only. Decide the fold strategy in step 2 and test "Édith" explicitly.
- **Artwork in a grid is loaded by the car's process.** The read grant on connect (phase 10) already covers it; a blank grid means the grant failed, not the style hint.
- **No em dashes or en dashes** in labels, bucket names, or search headers.

## Handoff

The car tree is now a first-class surface with its own search. Later phases can add Auto-only extras (download or explicit badges, progress on episodes) through `BrowseStyle` without touching the tree shape, and Android Automotive OS would reuse the same tree unchanged.
