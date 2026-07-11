package io.cloudcauldron.bocan.scrobble.providers

import io.cloudcauldron.bocan.scrobble.auth.TokenKeys
import io.cloudcauldron.bocan.scrobble.auth.TokenStore
import io.cloudcauldron.bocan.scrobble.net.ScrobbleHttp
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * ListenBrainz (https://listenbrainz.org). The user pastes a token from their profile;
 * it is stored encrypted and sent as a Bearer token. Payload and error handling are the
 * shared ListenBrainz-compatible behaviour.
 */
class ListenBrainzProvider(tokens: TokenStore, http: ScrobbleHttp, baseUrl: HttpUrl = DEFAULT_ENDPOINT.toHttpUrl()) :
    ListenBrainzCompatibleProvider(
        id = ProviderId.LISTENBRAINZ,
        displayName = "ListenBrainz",
        baseUrl = baseUrl,
        tokenKey = TokenKeys.LISTENBRAINZ_TOKEN,
        tokens = tokens,
        http = http
    ) {
    private companion object {
        const val DEFAULT_ENDPOINT = "https://api.listenbrainz.org/"
    }
}
