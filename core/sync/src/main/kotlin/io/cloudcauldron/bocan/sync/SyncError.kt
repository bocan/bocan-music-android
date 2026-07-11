package io.cloudcauldron.bocan.sync

/**
 * The one error hierarchy for the sync module. Every failure that crosses a
 * module boundary is one of these cases, each carrying enough context to log
 * and to map to a user-facing string resource in :app. The message text here is
 * for logs and developers, never shown directly: UI copy lives in strings.xml.
 *
 * Server machine codes from sync-protocol.md section 5 map to cases via
 * [fromServer]. Client-side ceremony and trust failures are their own cases.
 */
sealed class SyncError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    // Pairing ceremony (client-detected)

    /** The typed six-digit code did not match the code this device computed. */
    data object CodeMismatch : SyncError("pairing code did not match")

    /** Three wrong local code entries; the session is abandoned. */
    data object TooManyAttempts : SyncError("too many wrong code entries")

    // Server machine codes (sync-protocol.md section 5)

    /** The server no longer considers this device paired. */
    data object NotPaired : SyncError("server reports notPaired")

    /** Pairing mode ended (120 s elapsed or a fresh session is required). */
    data object PairingExpired : SyncError("server reports pairingExpired")

    /** The server rejected the confirm proof. */
    data object BadProof : SyncError("server reports badProof")

    /** The server is rate limiting; retry after [retryAfterSeconds] if present. */
    data class RateLimited(val retryAfterSeconds: Int?) : SyncError("server reports rateLimited")

    /** The requested resource does not exist on the server. */
    data class NotFound(val path: String) : SyncError("server reports notFound: $path")

    /** A library scan is mid-flight; the manifest would be torn. Retry later. */
    data class ServerBusy(val retryAfterSeconds: Int?) : SyncError("server reports busy")

    /** The server hit an internal error. */
    data object ServerInternal : SyncError("server reports internal")

    /** An error code the client does not specifically model. */
    data class Server(val machineCode: String, val httpStatus: Int, val serverMessage: String) :
        SyncError("server error $machineCode ($httpStatus): $serverMessage")

    // Trust and protocol

    /** The presented certificate did not hash to the expected pinned fingerprint. */
    data class CertificatePinMismatch(val expectedFingerprint: String, val actualFingerprint: String?) :
        SyncError("certificate pin mismatch (expected $expectedFingerprint, got $actualFingerprint)")

    /** The server speaks a protocol version this client cannot handle. */
    data class UnsupportedProtocol(val serverVersion: Int, val clientVersion: Int) :
        SyncError("unsupported protocol version $serverVersion (this client speaks $clientVersion)")

    /** A response was missing required fields or otherwise malformed. */
    data class MalformedResponse(val detail: String, val reason: Throwable? = null) : SyncError("malformed response: $detail", reason)

    // Sync engine (phase 03)

    /**
     * A file changed on the server since the manifest was fetched: the server
     * answered a download with 412 (If-Match failed). The engine refetches the
     * manifest and rebuilds the transfer plan (sync-protocol.md section 6).
     */
    data class ManifestStale(val url: String) : SyncError("file changed since manifest: $url")

    /**
     * A manifest relPath tried to escape the media root (contained "..", a leading
     * slash, an empty segment, or a backslash). The sync fails safe: no bytes are
     * written and the database is never touched.
     */
    data class UnsafePath(val relPath: String) : SyncError("unsafe relPath rejected: $relPath")

    /** Not enough free space to hold the pending transfer without risking a torn sync. */
    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) :
        SyncError("insufficient storage: need $requiredBytes bytes, have $availableBytes")

    /** External storage is unmounted, so getExternalFilesDir returned null. */
    data object MediaUnavailable : SyncError("external media storage is unavailable")

    // Transport

    /** A network or TLS failure below the HTTP layer. */
    class Network(val url: String?, cause: Throwable) : SyncError("network failure for ${url ?: "unknown url"}", cause)

    companion object {
        /** Map a server error body (sync-protocol.md section 5) to a typed case. */
        fun fromServer(machineCode: String, httpStatus: Int, serverMessage: String? = null, retryAfterSeconds: Int? = null): SyncError =
            when (machineCode) {
                "notPaired" -> NotPaired
                "pairingExpired" -> PairingExpired
                "badProof" -> BadProof
                "rateLimited" -> RateLimited(retryAfterSeconds)
                "notFound" -> NotFound(serverMessage.orEmpty())
                "busy" -> ServerBusy(retryAfterSeconds)
                "internal" -> ServerInternal
                else -> Server(machineCode, httpStatus, serverMessage.orEmpty())
            }
    }
}
