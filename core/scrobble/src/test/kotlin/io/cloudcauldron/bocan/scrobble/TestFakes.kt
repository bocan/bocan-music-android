package io.cloudcauldron.bocan.scrobble

import io.cloudcauldron.bocan.scrobble.auth.TokenStore
import io.cloudcauldron.bocan.scrobble.providers.ScrobbleProvider
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An in-memory [TokenStore] for tests, standing in for the Keystore-backed one. */
class FakeTokenStore(initial: Map<String, String> = emptyMap()) : TokenStore {
    private val flows = ConcurrentHashMap<String, MutableStateFlow<String?>>()

    init {
        initial.forEach { (key, value) -> flowFor(key).value = value }
    }

    override fun observe(key: String): Flow<String?> = flowFor(key).asStateFlow()
    override suspend fun get(key: String): String? = flowFor(key).value
    override suspend fun set(key: String, value: String) {
        flowFor(key).value = value
    }

    override suspend fun clear(key: String) {
        flowFor(key).value = null
    }

    private fun flowFor(key: String) = flows.getOrPut(key) { MutableStateFlow(null) }
}

/** A scriptable [ScrobbleProvider] that records calls and returns queued outcomes. */
class FakeProvider(
    override val id: String,
    private var authenticated: Boolean = true,
    private val outcome: (PlayEvent) -> SubmissionOutcome = { SubmissionOutcome.Success }
) : ScrobbleProvider {
    override val displayName: String = id
    override val authState: Flow<AuthState> = MutableStateFlow(AuthState.Connected("tester")).asStateFlow()

    val scrobbled = mutableListOf<PlayEvent>()
    val nowPlaying = mutableListOf<PlayEvent>()
    var scrobbleCalls = 0
        private set

    fun setAuthenticated(value: Boolean) {
        authenticated = value
    }

    override suspend fun isAuthenticated(): Boolean = authenticated

    override suspend fun updateNowPlaying(play: PlayEvent) {
        nowPlaying.add(play)
    }

    override suspend fun scrobble(batch: List<PlayEvent>): List<SubmissionResult> {
        scrobbleCalls++
        scrobbled.addAll(batch)
        return batch.map { SubmissionResult(it.queueId, outcome(it)) }
    }
}
