package io.cloudcauldron.bocan.app

import androidx.annotation.StringRes
import io.cloudcauldron.bocan.playback.PlaybackError
import io.cloudcauldron.bocan.sync.SyncError

/**
 * The one place typed errors become user copy. Both mappers are exhaustive
 * (no else branch), so adding an error case fails compilation here until it
 * has a human string; nothing falls through to a toString.
 */
@StringRes
fun SyncError.userMessageRes(): Int = when (this) {
    SyncError.CodeMismatch -> R.string.pairing_code_mismatch
    SyncError.TooManyAttempts -> R.string.pairing_too_many_attempts
    SyncError.NotPaired -> R.string.sync_state_failed_not_paired
    SyncError.PairingExpired -> R.string.pairing_error_expired
    SyncError.BadProof -> R.string.pairing_error_bad_proof
    is SyncError.RateLimited -> R.string.pairing_error_rate_limited
    is SyncError.NotFound -> R.string.sync_error_not_found
    is SyncError.ServerBusy -> R.string.pairing_error_busy
    SyncError.ServerInternal -> R.string.sync_error_internal
    is SyncError.Server -> R.string.sync_error_server
    is SyncError.CertificatePinMismatch -> R.string.pairing_error_pin
    is SyncError.UnsupportedProtocol -> R.string.pairing_error_unsupported
    is SyncError.MalformedResponse -> R.string.sync_error_malformed
    is SyncError.ManifestStale -> R.string.sync_error_stale
    is SyncError.UnsafePath -> R.string.sync_error_unsafe_path
    is SyncError.InsufficientStorage -> R.string.sync_state_failed_space
    SyncError.MediaUnavailable -> R.string.sync_error_media_unavailable
    is SyncError.Network -> R.string.pairing_error_network
}

@StringRes
fun PlaybackError.userMessageRes(): Int = when (this) {
    is PlaybackError.PlayerInitFailed -> R.string.playback_error_init
    is PlaybackError.UnknownMediaId -> R.string.playback_error_unknown_id
    is PlaybackError.ItemUnplayable -> R.string.playback_error_unplayable
    PlaybackError.MediaUnavailable -> R.string.playback_error_media_unavailable
    is PlaybackError.QueuePersistenceFailed -> R.string.playback_error_queue
}
