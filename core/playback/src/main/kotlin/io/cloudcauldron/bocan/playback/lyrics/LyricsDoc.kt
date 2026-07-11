package io.cloudcauldron.bocan.playback.lyrics

/** One timed lyric line: [text] shown at [timeMs] into the track. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * A parsed lyrics document. Either time-synced (LRC with timestamps) or a plain
 * unsynced block. The parser never throws: malformed input degrades to whichever of
 * these fits, and wholly untimed input becomes [Unsynced].
 */
sealed interface LyricsDoc {
    /**
     * Time-synced lyrics, lines sorted ascending by [LyricLine.timeMs]. [offsetMs] is
     * the `[offset:]` that was applied (0 when none), so the UI can show an offset chip.
     */
    data class Synced(val lines: List<LyricLine>, val offsetMs: Long = 0) : LyricsDoc

    /** Plain unsynced lyrics text (no usable timestamps). */
    data class Unsynced(val text: String) : LyricsDoc
}
