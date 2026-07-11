package io.cloudcauldron.bocan.playback.podcast

import io.cloudcauldron.bocan.playback.MediaId

/**
 * True when a media id is a podcast episode. Scrobble events carry this so phase 09 skips
 * podcasts, matching the Mac. Pure, so the exclusion is provable by test.
 */
fun isPodcastMedia(mediaId: String): Boolean = MediaId.parse(mediaId) is MediaId.Episode
