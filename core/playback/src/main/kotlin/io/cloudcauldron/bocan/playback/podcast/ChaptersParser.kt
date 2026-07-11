package io.cloudcauldron.bocan.playback.podcast

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A pure parser for the Podcasting 2.0 chapters JSON the Mac caches and relays. Times in
 * the document are seconds (fractional); this converts to milliseconds. Entries without a
 * start time are skipped, chapters are sorted by start, and malformed JSON yields an empty
 * list rather than throwing.
 */
object ChaptersParser {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun parse(body: String): List<Chapter> {
        val doc = decode(body) ?: return emptyList()
        return doc.chapters
            .mapNotNull { raw ->
                val start = raw.startTime ?: return@mapNotNull null
                Chapter(
                    title = raw.title.orEmpty(),
                    startTimeMs = (start * MS_PER_SECOND).toLong(),
                    endTimeMs = raw.endTime?.let { (it * MS_PER_SECOND).toLong() },
                    imageUrl = raw.img,
                    url = raw.url
                )
            }
            .sortedBy { it.startTimeMs }
    }

    // SerializationException extends IllegalArgumentException, so this single catch covers
    // both malformed JSON and out-of-range coercion; a bad document yields null, not a throw.
    private fun decode(body: String): ChaptersDoc? = try {
        json.decodeFromString(ChaptersDoc.serializer(), body)
    } catch (expected: IllegalArgumentException) {
        null
    }

    /**
     * The index of the chapter active at [positionMs] (start inclusive, end exclusive; a
     * missing end runs until the next chapter), or -1 when none applies. Assumes [chapters]
     * is sorted, as [parse] returns them.
     */
    fun activeChapterIndex(chapters: List<Chapter>, positionMs: Long): Int {
        for (index in chapters.indices) {
            val chapter = chapters[index]
            val end = chapter.endTimeMs ?: chapters.getOrNull(index + 1)?.startTimeMs ?: Long.MAX_VALUE
            if (positionMs >= chapter.startTimeMs && positionMs < end) return index
        }
        return -1
    }

    @Serializable
    private data class ChaptersDoc(val version: String? = null, val chapters: List<RawChapter> = emptyList())

    @Serializable
    private data class RawChapter(
        val startTime: Double? = null,
        val title: String? = null,
        val endTime: Double? = null,
        @SerialName("img") val img: String? = null,
        val url: String? = null
    )

    private const val MS_PER_SECOND = 1000.0
}
