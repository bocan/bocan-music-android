package io.cloudcauldron.bocan.scrobble

/**
 * The outcome of trying to submit one queued play, mapped from the provider's HTTP
 * response. The queue's state machine reads this to decide the row's next step:
 *
 *  - [Success]: delete the row.
 *  - [Retry]: back off and try again; after the attempt cap the row is dead-lettered.
 *  - [PermanentFailure]: dead-letter immediately (a 4xx the service will never accept).
 *  - [AuthExpired]: pause the whole provider without dropping the row (the user must
 *    reconnect); the item stays queued.
 */
sealed interface SubmissionOutcome {
    data object Success : SubmissionOutcome
    data class Retry(val reason: String, val retryAfterSec: Long? = null) : SubmissionOutcome
    data class PermanentFailure(val reason: String) : SubmissionOutcome
    data object AuthExpired : SubmissionOutcome
}

/** One provider result for the play with [queueId]. */
data class SubmissionResult(val queueId: Long, val outcome: SubmissionOutcome)

/** A provider's connection state, surfaced in settings. */
sealed interface AuthState {
    data object Disconnected : AuthState
    data class Connected(val username: String?) : AuthState
}
