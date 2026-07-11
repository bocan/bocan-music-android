package io.cloudcauldron.bocan.scrobble.queue

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.daos.ScrobbleDao
import io.cloudcauldron.bocan.persistence.entities.ScrobbleQueueEntity
import io.cloudcauldron.bocan.scrobble.CoroutineDispatchers
import io.cloudcauldron.bocan.scrobble.PlayEvent
import io.cloudcauldron.bocan.scrobble.SubmissionOutcome
import io.cloudcauldron.bocan.scrobble.providers.ScrobbleProvider
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The offline-resilient outbox over `scrobble_queue`. Every eligible play is enqueued
 * locally first (so a play is never lost to a dead network), then delivery is attempted.
 * The state machine:
 *
 *  - enqueue dedups on (provider, trackId, startedAt-rounded-to-minute) so a replayed or
 *    double-fired event is stored once;
 *  - drain submits due rows per provider in id order, batched up to 50, exactly once;
 *  - a retryable failure backs off per [RetryPolicy] (1 s, 2 s, ... cap 20 min), and after
 *    10 attempts the row is dead-lettered;
 *  - a permanent failure dead-letters immediately;
 *  - auth-expired pauses the provider without dropping its rows.
 *
 * The Mac scrobbling the same account is not a duplicate: dedup only guards the phone
 * against submitting its own play twice.
 */
// The queue surface (enqueue, drain, settle, retry, dead-letter, dedup, settings actions)
// is the phase contract; its breadth is intentional, not a decomposition smell.
@Suppress("TooManyFunctions")
class ScrobbleQueue(
    private val dao: ScrobbleDao,
    private val dispatchers: CoroutineDispatchers,
    private val clock: () -> Instant = Instant::now,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val log = AppLog.forCategory(LogCategory.Scrobble)

    /** Live queue depth (non-dead rows), for the settings status line. */
    fun observeQueueDepth(): Flow<Int> = dao.observeQueueSize()

    /** The dead-letter list, for settings. */
    fun observeDeadLettered(): Flow<List<ScrobbleQueueEntity>> = dao.observeDeadLettered()

    /**
     * Enqueue [play] for [providerId] unless an equivalent row already exists (same track
     * within the same minute). Returns the new row id, or null when deduplicated.
     */
    suspend fun enqueue(providerId: String, play: PlayEvent): Long? = withContext(dispatchers.io) {
        if (isDuplicate(providerId, play)) {
            log.debug("scrobble.queue.dedup", mapOf("provider" to providerId, "trackId" to play.trackId))
            return@withContext null
        }
        val id = dao.enqueue(ScrobbleQueueEntity(provider = providerId, payloadJson = json.encodeToString(PlayEvent.serializer(), play)))
        log.debug("scrobble.queue.enqueued", mapOf("provider" to providerId, "trackId" to play.trackId))
        id
    }

    /**
     * Drain all due rows for the [enabled] providers. Disabled providers, providers with
     * no implementation, and unauthenticated providers are skipped (their rows wait). Runs
     * on the IO dispatcher; never touches the audio thread.
     */
    suspend fun drain(providers: Map<String, ScrobbleProvider>, enabled: Set<String>) = withContext(dispatchers.io) {
        val due = dao.due(clock(), DRAIN_LIMIT).filter { it.provider in enabled && it.provider in providers }
        due.groupBy { it.provider }.forEach { (providerId, rows) ->
            val provider = providers.getValue(providerId)
            if (!provider.isAuthenticated()) {
                log.debug("scrobble.queue.paused", mapOf("provider" to providerId, "reason" to "unauthenticated"))
                return@forEach
            }
            rows.chunked(BATCH_SIZE).forEach { chunk -> submitChunk(provider, chunk) }
        }
    }

    private suspend fun submitChunk(provider: ScrobbleProvider, chunk: List<ScrobbleQueueEntity>) {
        val decoded = chunk.mapNotNull { row -> decode(row)?.let { row to it } }
        // A row whose payload will not decode can never be sent: dead-letter it.
        chunk.filter { row -> decoded.none { it.first.id == row.id } }.forEach { deadLetter(it, "undecodable payload") }
        if (decoded.isEmpty()) return

        val plays = decoded.map { (row, play) -> play.copy(queueId = row.id) }
        val rowsById = decoded.associate { (row, _) -> row.id to row }
        provider.scrobble(plays).forEach { result -> settle(rowsById.getValue(result.queueId), result.outcome) }
    }

    private suspend fun settle(row: ScrobbleQueueEntity, outcome: SubmissionOutcome) {
        when (outcome) {
            SubmissionOutcome.Success -> dao.delete(listOf(row.id))
            SubmissionOutcome.AuthExpired ->
                log.debug("scrobble.queue.authExpired", mapOf("provider" to row.provider, "id" to row.id))
            is SubmissionOutcome.PermanentFailure -> deadLetter(row, outcome.reason)
            is SubmissionOutcome.Retry -> retry(row, outcome.retryAfterSec)
        }
    }

    private suspend fun retry(row: ScrobbleQueueEntity, retryAfterSec: Long?) {
        val attempts = row.attempts + 1
        if (RetryPolicy.isExhausted(attempts)) {
            deadLetter(row, "exhausted after $attempts attempts")
            return
        }
        val nextAttemptAt = clock().plusSeconds(RetryPolicy.backoffSec(attempts, retryAfterSec))
        dao.recordAttempt(row.id, attempts, nextAttemptAt, deadLettered = false)
        log.debug("scrobble.queue.retry", mapOf("provider" to row.provider, "attempts" to attempts))
    }

    private suspend fun deadLetter(row: ScrobbleQueueEntity, reason: String) {
        dao.recordAttempt(row.id, row.attempts + 1, nextAttemptAt = null, deadLettered = true)
        log.warning("scrobble.queue.deadLettered", mapOf("provider" to row.provider, "id" to row.id, "reason" to reason))
    }

    /** Requeue a dead-lettered row for another attempt (settings action). */
    suspend fun retryDeadLettered(id: Long) = withContext(dispatchers.io) {
        dao.recordAttempt(id, attempts = 0, nextAttemptAt = null, deadLettered = false)
    }

    /** Discard a dead-lettered row (settings action). */
    suspend fun discard(id: Long) = withContext(dispatchers.io) { dao.delete(listOf(id)) }

    private suspend fun isDuplicate(providerId: String, play: PlayEvent): Boolean {
        val minute = play.playedAtEpochSec / SECONDS_PER_MINUTE
        return dao.activeForProvider(providerId).any { row ->
            decode(row)?.let { it.trackId == play.trackId && it.playedAtEpochSec / SECONDS_PER_MINUTE == minute } ?: false
        }
    }

    private fun decode(row: ScrobbleQueueEntity): PlayEvent? =
        runCatching { json.decodeFromString(PlayEvent.serializer(), row.payloadJson) }.getOrNull()

    private companion object {
        const val BATCH_SIZE = 50
        const val DRAIN_LIMIT = 500
        const val SECONDS_PER_MINUTE = 60L
    }
}
