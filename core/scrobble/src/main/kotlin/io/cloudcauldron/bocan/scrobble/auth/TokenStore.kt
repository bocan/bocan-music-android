package io.cloudcauldron.bocan.scrobble.auth

import kotlinx.coroutines.flow.Flow

/**
 * Encrypted credential storage for the providers. Values (Last.fm session key,
 * ListenBrainz and Rocksky tokens, usernames) are secrets and never touch the Room
 * database or plain preferences; the production implementation wraps them with an Android
 * Keystore key (see [KeystoreTokenStore]). Every value is redacted in logs by
 * `AppLog.sensitiveKeys`.
 *
 * [observe] is reactive so a provider's connection state updates the settings UI the
 * moment credentials are stored or cleared.
 */
interface TokenStore {
    /** Emits the current value for [key], then every change (null when absent). */
    fun observe(key: String): Flow<String?>

    /** The current value for [key], or null. */
    suspend fun get(key: String): String?

    /** Store [value] under [key]. */
    suspend fun set(key: String, value: String)

    /** Remove [key]. */
    suspend fun clear(key: String)
}

/** The credential keys each provider reads and writes. */
object TokenKeys {
    const val LAST_FM_SESSION = "lastfm.session_key"
    const val LAST_FM_USERNAME = "lastfm.username"
    const val LISTENBRAINZ_TOKEN = "listenbrainz.token"
    const val LISTENBRAINZ_USERNAME = "listenbrainz.username"
    const val ROCKSKY_KEY = "rocksky.api_key"
}
