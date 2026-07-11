package io.cloudcauldron.bocan.app.podcasts

/**
 * Sanitizes untrusted show-notes HTML (relayed from arbitrary feeds) before it is handed
 * to Html.fromHtml for rendering. Html.fromHtml never executes script, but scripts and
 * styles would otherwise leak into the rendered text, so their whole blocks are removed,
 * along with comments, inline event handlers, and javascript: URLs. Ordinary tags and
 * http(s) links are preserved. A pure function so it is unit tested as if hostile.
 */
object ShowNotesSanitizer {
    private val SCRIPT_OR_STYLE = Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>")
    private val COMMENT = Regex("(?s)<!--.*?-->")
    private val EVENT_HANDLER = Regex("(?i)\\son\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")
    private val JS_URI = Regex("(?i)(href|src)\\s*=\\s*[\"']\\s*javascript:[^\"']*[\"']")

    fun sanitize(html: String): String = html
        .replace(SCRIPT_OR_STYLE, "")
        .replace(COMMENT, "")
        .replace(EVENT_HANDLER, "")
        .replace(JS_URI) { match -> "${match.groupValues[1]}=\"#\"" }
        .trim()
}
