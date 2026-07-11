package io.cloudcauldron.bocan.app.settings

import io.cloudcauldron.bocan.app.data.ScrobbleSettingsSource
import io.cloudcauldron.bocan.persistence.entities.ScrobbleQueueEntity
import io.cloudcauldron.bocan.scrobble.AuthState
import io.cloudcauldron.bocan.scrobble.CoroutineDispatchers
import io.cloudcauldron.bocan.scrobble.PlayEvent
import io.cloudcauldron.bocan.scrobble.auth.TokenKeys
import io.cloudcauldron.bocan.scrobble.auth.TokenStore
import io.cloudcauldron.bocan.scrobble.providers.LastFmProvider
import io.cloudcauldron.bocan.scrobble.providers.ProviderId
import io.cloudcauldron.bocan.scrobble.providers.ScrobbleProvider
import io.cloudcauldron.bocan.scrobble.queue.ScrobbleQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** One provider row in the scrobble settings. */
data class ScrobbleProviderRow(val id: String, val displayName: String, val enabled: Boolean, val connected: Boolean, val username: String?)

/** One dead-lettered submission, shown with retry and discard actions. */
data class DeadLetterRow(val id: Long, val provider: String, val title: String)

/** What the scrobble settings screen renders. */
data class ScrobbleSettingsUiState(
    val masterEnabled: Boolean = false,
    val providers: List<ScrobbleProviderRow> = emptyList(),
    val queueDepth: Int = 0,
    val deadLettered: List<DeadLetterRow> = emptyList()
)

/**
 * Drives the scrobble settings screen: the master switch, per-provider enable and
 * connect state, the queue depth, and the dead-letter list with retry and discard. Every
 * edit writes to the DataStore toggles or the encrypted [TokenStore]; nothing here holds
 * a credential in the clear beyond the pending Last.fm auth token during the web flow.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class ScrobbleSettingsViewModel(
    private val providers: List<ScrobbleProvider>,
    private val settings: ScrobbleSettingsSource,
    private val queue: ScrobbleQueue,
    private val tokens: TokenStore,
    private val lastFm: LastFmProvider?,
    dispatchers: CoroutineDispatchers
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val json = Json { ignoreUnknownKeys = true }
    private var pendingLastFmToken: String? = null

    private val authStates = if (providers.isEmpty()) flowOf(emptyList()) else combine(providers.map { it.authState }) { it.toList() }

    val state: StateFlow<ScrobbleSettingsUiState> =
        combine(authStates, settings.toggles, queue.observeQueueDepth(), queue.observeDeadLettered()) { auths, toggles, depth, dead ->
            ScrobbleSettingsUiState(
                masterEnabled = toggles.masterEnabled,
                providers = providers.mapIndexed { index, provider ->
                    val auth = auths.getOrNull(index) ?: AuthState.Disconnected
                    ScrobbleProviderRow(
                        id = provider.id,
                        displayName = provider.displayName,
                        enabled = provider.id in toggles.enabledProviders,
                        connected = auth is AuthState.Connected,
                        username = (auth as? AuthState.Connected)?.username
                    )
                },
                queueDepth = depth,
                deadLettered = dead.map(::toDeadLetterRow)
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), ScrobbleSettingsUiState())

    fun setMasterEnabled(enabled: Boolean) = launch { settings.setMasterEnabled(enabled) }

    fun setProviderEnabled(providerId: String, enabled: Boolean) = launch { settings.setProviderEnabled(providerId, enabled) }

    /** Store a pasted token for a ListenBrainz-compatible provider. */
    fun connectWithToken(providerId: String, token: String) = launch {
        tokenKeyFor(providerId)?.let { tokens.set(it, token.trim()) }
    }

    /** Forget a provider's credentials. */
    fun disconnect(providerId: String) = launch {
        when (providerId) {
            ProviderId.LAST_FM -> lastFm?.disconnect()
            else -> tokenKeyFor(providerId)?.let { tokens.clear(it) }
        }
    }

    /** Begin the Last.fm web auth flow; returns the browser URL to open, or null if unavailable. */
    @Suppress("ReturnCount") // early exits for a missing provider and a failed token request
    suspend fun beginLastFmAuth(): String? {
        val provider = lastFm ?: return null
        val token = runCatching { provider.requestAuthToken() }.getOrNull() ?: return null
        pendingLastFmToken = token
        return provider.authorizationUrl(token).toString()
    }

    /** Complete the Last.fm flow after the user authorised the token in the browser. */
    fun finishLastFmAuth() = launch {
        val provider = lastFm ?: return@launch
        val token = pendingLastFmToken ?: return@launch
        runCatching { provider.completeAuth(token) }
        pendingLastFmToken = null
    }

    fun retryDeadLettered(id: Long) = launch { queue.retryDeadLettered(id) }

    fun discardDeadLettered(id: Long) = launch { queue.discard(id) }

    fun dispose() = scope.cancel()

    private fun toDeadLetterRow(row: ScrobbleQueueEntity): DeadLetterRow {
        val title = runCatching { json.decodeFromString(PlayEvent.serializer(), row.payloadJson).title }.getOrDefault("")
        return DeadLetterRow(row.id, row.provider, title)
    }

    private fun tokenKeyFor(providerId: String): String? = when (providerId) {
        ProviderId.LISTENBRAINZ -> TokenKeys.LISTENBRAINZ_TOKEN
        ProviderId.ROCKSKY -> TokenKeys.ROCKSKY_KEY
        else -> null
    }

    private fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
