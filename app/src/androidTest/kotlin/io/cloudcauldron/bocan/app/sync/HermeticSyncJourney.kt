package io.cloudcauldron.bocan.app.sync

import android.app.Notification
import android.app.NotificationManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.cloudcauldron.bocan.app.AlbumScreen
import io.cloudcauldron.bocan.app.E2ETest
import io.cloudcauldron.bocan.app.LibraryScreen
import io.cloudcauldron.bocan.app.MiniPlayer
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.sync.engine.MediaLayout
import io.cloudcauldron.bocan.sync.engine.SyncState
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/**
 * Pairing and sync over the wire (phase 17 slice 6), from Settings, Sync, Pair: the app pairs
 * with the loopback [FakeMac], pins it, and syncs the fixture library through every endpoint
 * the protocol names. This is the only place in the suite where the app opens a socket, and
 * the socket is 127.0.0.1 inside the test process.
 */
class HermeticSyncJourney : E2ETest(FixtureProfile.Unpaired) {
    @get:Rule
    val fakeMac = FakeMacRule()

    private val mac get() = fakeMac.mac

    @Test
    fun pairingFromSyncSettingsSyncsTheLibraryOverTheWire() {
        pairFromSyncSettings()
        awaitState("every fixture track downloaded", SYNC_TIMEOUT_MS) { downloaded().size == mac.manifest.tracks.size }

        // Both sides pinned each other, by the certificates the TLS layer saw.
        val server = assertNotNull(runBlocking { graph.database.syncDao().server() }, "no paired server stored")
        assertEquals(mac.fingerprint, server.certFingerprint, "the phone pinned another certificate")
        assertEquals(mac.manifest.serverId, server.serverId)
        assertEquals(setOf(graph.deviceIdentity.fingerprint), mac.trustedPhones.toSet(), "the Mac trusts another phone")
        awaitState("generation ${mac.manifest.generation} applied") {
            runBlocking { graph.database.syncDao().server()?.lastAppliedGeneration } == mac.manifest.generation
        }

        // Every file on the phone is the file the manifest describes.
        val layout = MediaLayout(app)
        mac.manifest.tracks.distinctBy { it.relPath }.forEach { track ->
            assertEquals(track.sha256, sha256(layout.trackFile(track.relPath)), "the bytes of ${track.relPath}")
        }
        mac.manifest.episodes.forEach { episode ->
            assertEquals(episode.sha256, sha256(layout.episodeFile(episode.relPath)), "the bytes of ${episode.relPath}")
        }

        // A synced track plays, and its lyrics come from the Mac when they are first opened.
        val library = LibraryScreen(ui)
        library.open()
        library.albumsTab()
        library.openAlbum(LYRICS_ALBUM)
        AlbumScreen(ui).playTrack(LYRICS_TRACK)
        awaitPlayer("'$LYRICS_TRACK' playing from the synced file") { it.current?.title == LYRICS_TRACK && it.isPlaying }
        MiniPlayer(ui).open()
        ui.tap(E2ETags.NowPlaying.QUICK_LYRICS)
        ui.awaitRow(E2ETags.Rows.LYRIC_LINE, LYRIC_LINE)
        val lyricsId = mac.manifest.tracks.single { it.title == LYRICS_TRACK }.id
        assertTrue("/v1/lyrics/$lyricsId" in mac.requests, "the lyrics did not come from the Mac: ${mac.requests.distinct()}")
    }

    @Test
    fun theProgressNotificationCountsTheFilesAndItsCancelStopsTheSync() {
        mac.slowFiles()
        pairFromSyncSettings()
        awaitState("the sync transferring, slowed by the Mac", SYNC_TIMEOUT_MS) { syncState() is SyncState.Transferring }
        val total = (syncState() as SyncState.Transferring).filesTotal

        var seen: Notification? = null
        awaitState("the sync notification counting toward $total files, with a Cancel action") {
            seen = syncNotification()
            val extras = seen?.extras
            extras?.getInt(Notification.EXTRA_PROGRESS_MAX) == total &&
                extras.getInt(Notification.EXTRA_PROGRESS) < total &&
                seen?.actions.orEmpty().any { it.title == cancelLabel() }
        }
        // Cancel from the shade, as a person would.
        seen!!.actions.single { it.title == cancelLabel() }.actionIntent.send()
        awaitState("the sync stopped and its notification gone") { syncState() !is SyncState.Transferring && syncNotification() == null }
        assertEquals(0, downloaded().size, "files downloaded after the sync was cancelled")

        mac.normalFiles()
        ui.tap(E2ETags.Nav.SETTINGS)
        ui.tap(E2ETags.Settings.SYNC)
        ui.tap(E2ETags.Sync.SYNC_NOW)
        awaitState("every fixture track downloaded after Sync now", SYNC_TIMEOUT_MS) { downloaded().size == mac.manifest.tracks.size }
        awaitState("the sync notification gone after the sync") { syncNotification() == null }
    }

    private fun pairFromSyncSettings() {
        ui.tap(E2ETags.Nav.SETTINGS)
        ui.tap(E2ETags.Settings.SYNC)
        ui.tap(E2ETags.Sync.PAIR)
        ui.awaitRow(E2ETags.Pairing.MAC_ROW, FakeMac.SERVICE_NAME).performClick()
        awaitState("the Mac showing a code") { mac.shownCode != null }
        ui.awaitTag(E2ETags.Pairing.CODE).performTextInput(checkNotNull(mac.shownCode))
        // Paired: the route stores the Mac, starts the first sync, and leaves for the library,
        // which has no tabs until the first tracks land.
        awaitState("paired, and off the pairing screen") {
            runBlocking { graph.database.syncDao().server() } != null &&
                composeRule.onAllNodes(hasTestTag(E2ETags.Pairing.CODE), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        screenshot("after-pairing")
    }

    private fun syncState(): SyncState = graph.syncCoordinator.syncState.value

    private fun downloaded() = runBlocking { graph.database.syncDao().allTracks().filter { it.downloadState == DownloadState.Downloaded } }

    private fun syncNotification(): Notification? = app.getSystemService(NotificationManager::class.java)
        .activeNotifications
        .map { it.notification }
        .firstOrNull { it.channelId == SYNC_CHANNEL }

    private fun cancelLabel(): String = app.getString(io.cloudcauldron.bocan.sync.R.string.sync_notification_cancel)

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SYNC_TIMEOUT_MS = 60_000L

        // :core:sync's SyncNotifications.CHANNEL_ID, which is internal to that module.
        const val SYNC_CHANNEL = "bocan.sync"

        // From the fixture's lyrics for First Light 1 (lyrics/9001000001.lrc), served by the fake Mac.
        const val LYRICS_ALBUM = "First Light"
        const val LYRICS_TRACK = "First Light 1"
        const val LYRIC_LINE = "Third line."
    }
}
