package io.cloudcauldron.bocan.scrobble.providers

import io.cloudcauldron.bocan.scrobble.auth.TokenKeys
import io.cloudcauldron.bocan.scrobble.auth.TokenStore
import io.cloudcauldron.bocan.scrobble.net.ScrobbleHttp
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Rocksky (https://rocksky.app). It exposes a ListenBrainz-compatible `submit-listens`
 * API at audioscrobbler.rocksky.app; the user's API key (from rocksky.app/apikeys) is
 * sent as a Bearer token. Matches the Mac's `RockskyProvider`.
 */
class RockskyProvider(tokens: TokenStore, http: ScrobbleHttp, baseUrl: HttpUrl = DEFAULT_ENDPOINT.toHttpUrl()) :
    ListenBrainzCompatibleProvider(
        id = ProviderId.ROCKSKY,
        displayName = "Rocksky",
        baseUrl = baseUrl,
        tokenKey = TokenKeys.ROCKSKY_KEY,
        tokens = tokens,
        http = http
    ) {
    private companion object {
        const val DEFAULT_ENDPOINT = "https://audioscrobbler.rocksky.app/"
    }
}
