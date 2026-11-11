package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.scrobble.providers.ProviderId

/**
 * Every crawl table, and the only tags allowed to sit outside them. A tag on screen must be
 * the tag of some table's step, or be listed here with its reason; `TableCoverageTest` also
 * checks that every constant in `E2ETags` is accounted for, and that nothing listed here is
 * secretly crawled (a stale entry).
 */
object CrawlRegistry {
    fun tables(c: CrawlContext): Map<String, List<Step>> = mapOf(
        "library" to LibraryCrawl.table(c),
        "detail screens" to DetailCrawl.table(c),
        "search" to SearchCrawl.table(c),
        "podcasts" to PodcastsCrawl.table(c),
        "podcast player" to PodcastPlayerCrawl.table(c),
        "now playing" to NowPlayingCrawl.table(c),
        "settings" to SettingsCrawl.table(c),
        "equalizer" to EqualizerCrawl.table(c),
        "sync" to SyncCrawl.table(c),
        "first run" to FirstRunCrawl.table(c),
        "empty library" to EmptyLibraryCrawl.table(c)
    )

    /** Real controls no table can reach yet, each with the reason and the slice that will. */
    val NOT_CRAWLED: Map<String, String> = mapOf(
        E2ETags.Pairing.MAC_ROW to "needs a Mac advertising on the network: slice 6's loopback fake Mac",
        E2ETags.Pairing.CODE to "the code step follows choosing a Mac: slice 6",
        E2ETags.Pairing.DONE to "follows a successful pairing: slice 6",
        E2ETags.Pairing.START_OVER to "follows a failed pairing with a real peer: slice 6",
        E2ETags.Onboarding.DONE to "the welcome flow's first-sync page follows a pairing: slice 6",
        E2ETags.Onboarding.SYNC_LATER to "the welcome flow's first-sync page follows a pairing: slice 6",
        E2ETags.Common.LOCAL_NETWORK_ALLOW to "shown only on Android 17 with the local network permission denied; the suite runs on API 36",
        E2ETags.Scrobble.TOKEN_SAVE to "saving checks the token with the provider over the internet, which the suite never touches",
        E2ETags.Scrobble.DEAD_LETTER_RETRY to "needs a scrobble that failed past its retries, which needs a connected provider",
        E2ETags.Scrobble.DEAD_LETTER_DISCARD to "needs a scrobble that failed past its retries, which needs a connected provider",
        E2ETags.Scrobble.provider(ProviderId.LAST_FM, E2ETags.Scrobble.CONNECT) to
            "starts a browser sign-in with Last.fm over the internet"
    )

    /** Tags on things that are not controls: containers and pictures used as markers and scroll targets. */
    val NOT_ACTIONABLE: Set<String> = setOf(
        E2ETags.Library.ALBUMS_GRID,
        E2ETags.Podcasts.SHOWS_GRID,
        E2ETags.MiniPlayer.ARTWORK,
        // The tagged picture; Material's wrapper nodes around it own the actions (see TagAllowlist).
        E2ETags.Common.SHEET_HANDLE
    )

    fun stepTags(c: CrawlContext): Set<String> = tables(c).values.flatten().map { it.tag }.toSet()

    fun accountedFor(c: CrawlContext): Set<String> = stepTags(c) + NOT_CRAWLED.keys + NOT_ACTIONABLE
}
