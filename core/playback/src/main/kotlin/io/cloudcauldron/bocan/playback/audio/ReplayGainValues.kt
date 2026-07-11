package io.cloudcauldron.bocan.playback.audio

/**
 * The ReplayGain metadata for one item, carried on `MediaItem.localConfiguration.tag`
 * so the audio thread can read the right gain when the item becomes current. Any
 * field may be null when the Mac did not compute it. Gains and peaks come straight
 * from the track row (rgTrackGain, rgTrackPeak, rgAlbumGain, rgAlbumPeak).
 */
data class ReplayGainValues(val trackGainDb: Double?, val trackPeak: Double?, val albumGainDb: Double?, val albumPeak: Double?) {
    companion object {
        /** An item with no ReplayGain data at all (episodes, un-analysed tracks). */
        val NONE = ReplayGainValues(null, null, null, null)
    }
}
