package io.cloudcauldron.bocan.scrobble.providers

import java.security.MessageDigest

/**
 * Builds the `api_sig` parameter every authenticated Last.fm method requires.
 *
 * Per https://www.last.fm/api/desktopauth : concatenate every parameter except `format`
 * and `callback` into a single string in alphabetical key order as `key1value1key2value2`,
 * append the shared secret, then take the lower-case hex MD5 digest. Pure so it is tested
 * against the known-answer vector in the docs.
 */
object LastFmSignature {
    /** Parameter names excluded from the signature. */
    val excludedKeys = setOf("format", "callback")

    /** The lower-case hex MD5 digest for [params] and [secret]. */
    fun sign(params: Map<String, String>, secret: String): String {
        val concatenated = params
            .filterKeys { it !in excludedKeys }
            .toSortedMap()
            .entries
            .joinToString(separator = "") { "${it.key}${it.value}" }
        val digest = MessageDigest.getInstance("MD5").digest((concatenated + secret).toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
