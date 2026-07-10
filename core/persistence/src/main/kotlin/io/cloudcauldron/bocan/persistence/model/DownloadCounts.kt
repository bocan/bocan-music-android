package io.cloudcauldron.bocan.persistence.model

/** Track download-state tallies for the sync UI. */
data class DownloadCounts(val pending: Int, val downloaded: Int, val failed: Int)
