package io.cloudcauldron.bocan.playback.browse

import java.text.Normalizer
import java.util.Locale

/**
 * The letter index the car shows for long lists (phase 15). A name lands in "A" to "Z",
 * "0-9", or "#", decided by its first character after a leading "The " or "A " is
 * skipped and accents are folded. This object is the single definition of a bucket:
 * SQLite cannot fold accents, so the tree groups names here, never in a query.
 */
object LetterBuckets {
    /** The bucket for names that start with a digit. */
    const val DIGITS = "0-9"

    /** The bucket for names that start with anything that is not A to Z or a digit. */
    const val OTHER = "#"

    /** Every bucket in display order: A to Z, then digits, then everything else. */
    val ORDER: List<String> = ('A'..'Z').map(Char::toString) + DIGITS + OTHER

    private val ARTICLES = listOf("THE ", "A ")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /** The bucket key for [name]. Pure. */
    fun keyFor(name: String): String {
        val first = sortName(name).firstOrNull() ?: return OTHER
        return when (first) {
            in 'A'..'Z' -> first.toString()
            in '0'..'9' -> DIGITS
            else -> OTHER
        }
    }

    /**
     * Group [rows] by the bucket of [name]: only non-empty buckets, in [ORDER], each
     * bucket's members sorted by the same folded, article-free name the key comes from.
     */
    fun <T> group(rows: List<T>, name: (T) -> String): Map<String, List<T>> {
        val byKey = rows.groupBy { keyFor(name(it)) }
        val alphabetical = compareBy<T>({ sortName(name(it)) }, { name(it) })
        return ORDER.mapNotNull { key -> byKey[key]?.let { key to it.sortedWith(alphabetical) } }.toMap()
    }

    /** The upper-cased, accent-folded name with a leading article removed when a word follows it. */
    private fun sortName(name: String): String {
        val folded = COMBINING_MARKS.replace(Normalizer.normalize(name.trim(), Normalizer.Form.NFD), "").uppercase(Locale.ROOT)
        val article = ARTICLES.firstOrNull { folded.startsWith(it) && folded.length > it.length }
        return if (article == null) folded else folded.substring(article.length).trimStart()
    }
}
