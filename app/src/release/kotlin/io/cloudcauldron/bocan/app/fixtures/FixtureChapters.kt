package io.cloudcauldron.bocan.app.fixtures

import android.content.Context
import io.cloudcauldron.bocan.playback.podcast.ChaptersFetcher

/** Release builds carry no end-to-end fixture, so there are no fixture chapters to serve. */
internal fun Context.fixtureChaptersFetcher(): ChaptersFetcher? = null
