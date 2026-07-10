package io.cloudcauldron.bocan.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncErrorTests {
    @Test
    fun `fromServer maps every known machine code to its case`() {
        assertTrue(SyncError.fromServer("notPaired", 403) is SyncError.NotPaired)
        assertTrue(SyncError.fromServer("pairingExpired", 410) is SyncError.PairingExpired)
        assertTrue(SyncError.fromServer("badProof", 400) is SyncError.BadProof)
        assertTrue(SyncError.fromServer("internal", 500) is SyncError.ServerInternal)
        assertEquals(SyncError.RateLimited(30), SyncError.fromServer("rateLimited", 429, retryAfterSeconds = 30))
        assertEquals(SyncError.ServerBusy(5), SyncError.fromServer("busy", 503, retryAfterSeconds = 5))
        assertEquals(SyncError.NotFound("/v1/file/track/9"), SyncError.fromServer("notFound", 404, "/v1/file/track/9"))
    }

    @Test
    fun `fromServer keeps unknown codes in the fallback case`() {
        val error = SyncError.fromServer("teapot", 418, "short and stout")
        assertTrue(error is SyncError.Server)
        error as SyncError.Server
        assertEquals("teapot", error.machineCode)
        assertEquals(418, error.httpStatus)
        assertEquals("short and stout", error.serverMessage)
    }

    @Test
    fun `every error carries a non-blank log message`() {
        val samples = listOf(
            SyncError.CodeMismatch,
            SyncError.TooManyAttempts,
            SyncError.CertificatePinMismatch("aa", "bb"),
            SyncError.UnsupportedProtocol(serverVersion = 2, clientVersion = 1),
            SyncError.MalformedResponse("missing sessionId"),
            SyncError.Network(url = "https://mac/v1/ping", cause = RuntimeException("boom"))
        )
        samples.forEach { assertTrue(requireNotNull(it.message).isNotBlank()) }
    }
}
