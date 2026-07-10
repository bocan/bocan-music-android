package io.cloudcauldron.bocan.sync.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** pair/start request body (sync-protocol.md section 4 step 2). */
@Serializable
internal data class PairStartRequest(val protocolVersion: Int, val deviceName: String, val noncePhone: String)

/** pair/start response body. */
@Serializable
internal data class PairStartResponse(val protocolVersion: Int, val serverName: String, val nonceMac: String, val sessionId: String)

/** pair/confirm request body (sync-protocol.md section 4 step 5). */
@Serializable
internal data class PairConfirmRequest(val sessionId: String, val proof: String)

/** pair/confirm response body. */
@Serializable
internal data class PairConfirmResponse(val status: String, val serverId: String)

/** The error envelope every endpoint uses on failure (sync-protocol.md section 5). */
@Serializable
internal data class ErrorBody(val error: String, val message: String? = null)

/** JSON for the pairing wire format: tolerant of unknown fields, omits nulls. */
internal val SyncJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
