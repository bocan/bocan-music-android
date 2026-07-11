package io.cloudcauldron.bocan.app.podcasts

/**
 * The link policy for show notes: only https links open without a prompt. Anything else
 * (http, or an odd scheme the sanitizer let through) is treated as insecure and must be
 * confirmed before it opens, since the feed HTML is untrusted.
 */
object ShowNotesLinks {
    fun isSecure(url: String): Boolean = url.trim().startsWith("https://", ignoreCase = true)
}
