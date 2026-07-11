package io.cloudcauldron.bocan.persistence.model

/** The number of unplayed episodes for one podcast, for the shows-grid badge. */
data class UnplayedCount(val podcastId: Long, val unplayed: Int)
