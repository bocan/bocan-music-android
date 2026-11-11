package io.cloudcauldron.bocan.app

import io.cloudcauldron.bocan.app.surfaces.DetailCrawl
import io.cloudcauldron.bocan.app.surfaces.EmptyLibraryCrawl
import io.cloudcauldron.bocan.app.surfaces.EqualizerCrawl
import io.cloudcauldron.bocan.app.surfaces.FirstRunCrawl
import io.cloudcauldron.bocan.app.surfaces.LandscapeCrawl
import io.cloudcauldron.bocan.app.surfaces.LandscapeEmptyLibraryCrawl
import io.cloudcauldron.bocan.app.surfaces.LandscapeFirstRunCrawl
import io.cloudcauldron.bocan.app.surfaces.LandscapePairingCrawl
import io.cloudcauldron.bocan.app.surfaces.LibraryCrawl
import io.cloudcauldron.bocan.app.surfaces.NowPlayingCrawl
import io.cloudcauldron.bocan.app.surfaces.PairingCrawl
import io.cloudcauldron.bocan.app.surfaces.PodcastPlayerCrawl
import io.cloudcauldron.bocan.app.surfaces.PodcastsCrawl
import io.cloudcauldron.bocan.app.surfaces.SearchCrawl
import io.cloudcauldron.bocan.app.surfaces.SettingsCrawl
import io.cloudcauldron.bocan.app.surfaces.SyncCrawl
import io.cloudcauldron.bocan.app.surfaces.TableCoverageTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * The surface crawls, the same tables again in landscape, and the table coverage check alone
 * (`make end-to-end-crawl`); `E2ESuite` runs them too.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    TableCoverageTest::class,
    LibraryCrawl::class,
    DetailCrawl::class,
    SearchCrawl::class,
    PodcastsCrawl::class,
    PodcastPlayerCrawl::class,
    NowPlayingCrawl::class,
    SettingsCrawl::class,
    EqualizerCrawl::class,
    SyncCrawl::class,
    FirstRunCrawl::class,
    EmptyLibraryCrawl::class,
    PairingCrawl::class,
    LandscapeCrawl::class,
    LandscapeFirstRunCrawl::class,
    LandscapePairingCrawl::class,
    LandscapeEmptyLibraryCrawl::class
)
class CrawlSuite
