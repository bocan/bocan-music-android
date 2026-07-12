package io.cloudcauldron.bocan.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.playback.PlaybackError
import io.cloudcauldron.bocan.sync.SyncError
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Every sealed error case maps to a non-empty string resource. The mappers'
 * exhaustive whens make missing cases a compile error; this test proves the
 * resources they point at actually resolve to real copy.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ErrorMessagesTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val syncErrors = listOf(
        SyncError.CodeMismatch,
        SyncError.TooManyAttempts,
        SyncError.NotPaired,
        SyncError.PairingExpired,
        SyncError.BadProof,
        SyncError.RateLimited(retryAfterSeconds = 5),
        SyncError.NotFound("/v1/file/track/1"),
        SyncError.ServerBusy(retryAfterSeconds = 5),
        SyncError.ServerInternal,
        SyncError.Server("weird", 500, "boom"),
        SyncError.CertificatePinMismatch("aa", "bb"),
        SyncError.UnsupportedProtocol(serverVersion = 9, clientVersion = 1),
        SyncError.MalformedResponse("missing field"),
        SyncError.ManifestStale("url"),
        SyncError.UnsafePath("../evil"),
        SyncError.InsufficientStorage(requiredBytes = 10, availableBytes = 1),
        SyncError.MediaUnavailable,
        SyncError.Network("url", cause = RuntimeException("io"))
    )

    private val playbackErrors = listOf(
        PlaybackError.PlayerInitFailed(RuntimeException("init")),
        PlaybackError.UnknownMediaId("nonsense:1"),
        PlaybackError.ItemUnplayable("track:1"),
        PlaybackError.MediaUnavailable,
        PlaybackError.QueuePersistenceFailed(RuntimeException("disk"))
    )

    @Test
    fun `every sync error resolves to non-empty copy`() {
        syncErrors.forEach { error ->
            val text = context.getString(error.userMessageRes())
            assertTrue("empty message for $error", text.isNotBlank())
        }
    }

    @Test
    fun `every playback error resolves to non-empty copy`() {
        playbackErrors.forEach { error ->
            val text = context.getString(error.userMessageRes())
            assertTrue("empty message for $error", text.isNotBlank())
        }
    }
}
