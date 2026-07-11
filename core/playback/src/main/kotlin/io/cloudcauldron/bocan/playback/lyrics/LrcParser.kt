package io.cloudcauldron.bocan.playback.lyrics

/**
 * A pure LRC parser, mirroring the Mac's parser. It never throws: malformed lines are
 * skipped, and input with no usable timestamps becomes [LyricsDoc.Unsynced].
 *
 * Handles multiple timestamps per line (`[01:02.00][02:15.50]chorus` yields two timed
 * lines), centisecond (`.xx`) and millisecond (`.xxx`) fractions, metadata tags
 * (`[ar:]`, `[ti:]`, ...), and `[offset:+500]` applied to every line (clamped at 0).
 */
object LrcParser {
    private val TIMESTAMP = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val METADATA = Regex("""\[([a-zA-Z]+):(.*?)]""")
    private const val SECONDS_PER_MINUTE = 60L
    private const val MS_PER_SECOND = 1000L
    private const val DECISECOND_DIGITS = 1
    private const val CENTISECOND_DIGITS = 2
    private const val MILLISECOND_DIGITS = 3
    private const val DECISECOND_MS = 100L
    private const val CENTISECOND_MS = 10L
    private const val GROUP_MINUTES = 1
    private const val GROUP_SECONDS = 2
    private const val GROUP_FRACTION = 3

    fun parse(raw: String): LyricsDoc {
        val offset = extractOffset(raw)
        val lines = ArrayList<LyricLine>()
        for (line in raw.lineSequence()) {
            val timestamps = TIMESTAMP.findAll(line).toList()
            if (timestamps.isEmpty()) continue
            val text = line.substring(timestamps.last().range.last + 1).trim()
            for (match in timestamps) {
                val timeMs = timeOf(match) + offset
                lines += LyricLine(timeMs.coerceAtLeast(0), text)
            }
        }
        return if (lines.isEmpty()) {
            LyricsDoc.Unsynced(raw.trim())
        } else {
            LyricsDoc.Synced(lines.sortedBy { it.timeMs }, offset)
        }
    }

    private fun timeOf(match: MatchResult): Long {
        val minutes = match.groupValues[GROUP_MINUTES].toLong()
        val seconds = match.groupValues[GROUP_SECONDS].toLong()
        val fraction = match.groupValues[GROUP_FRACTION]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            DECISECOND_DIGITS -> fraction.toLong() * DECISECOND_MS
            CENTISECOND_DIGITS -> fraction.toLong() * CENTISECOND_MS
            else -> fraction.take(MILLISECOND_DIGITS).toLong()
        }
        return (minutes * SECONDS_PER_MINUTE + seconds) * MS_PER_SECOND + fractionMs
    }

    private fun extractOffset(raw: String): Long {
        for (match in METADATA.findAll(raw)) {
            if (match.groupValues[1].equals("offset", ignoreCase = true)) {
                val value = match.groupValues[2].trim().removePrefix("+")
                return value.toLongOrNull() ?: 0L
            }
        }
        return 0L
    }
}
