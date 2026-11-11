package io.cloudcauldron.bocan.app.fixtures

import android.content.Context
import io.cloudcauldron.bocan.playback.podcast.ChaptersFetcher
import java.io.IOException

/**
 * Debug builds only: the end-to-end fixture's chapters, straight from `assets/fixture/chapters/`,
 * the way the phase 14 demo serves its own. Null for any other episode, so the caller falls
 * through to the Mac. The release source set has a stub that returns no fetcher at all.
 */
internal fun Context.fixtureChaptersFetcher(): ChaptersFetcher? {
    val assets = applicationContext.assets
    return ChaptersFetcher { episodeId ->
        if (!episodeId.startsWith(EPISODE_ID_PREFIX)) {
            null
        } else {
            try {
                assets.open("$CHAPTERS_DIR/$episodeId.json").use { it.readBytes().decodeToString() }
            } catch (missing: IOException) {
                null
            }
        }
    }
}

private const val EPISODE_ID_PREFIX = "fixture-episode-"
private const val CHAPTERS_DIR = "fixture/chapters"
