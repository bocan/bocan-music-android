package io.cloudcauldron.bocan.app.system

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Process
import androidx.core.os.BundleCompat
import io.cloudcauldron.bocan.app.AlbumScreen
import io.cloudcauldron.bocan.app.E2ETest
import io.cloudcauldron.bocan.app.LibraryScreen
import io.cloudcauldron.bocan.app.MiniPlayer
import io.cloudcauldron.bocan.app.NowPlayingScreen
import io.cloudcauldron.bocan.app.PodcastsScreen
import io.cloudcauldron.bocan.app.ShowScreen
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.widget.WidgetState
import io.cloudcauldron.bocan.app.widget.WidgetStateStore
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * What the app shows outside its own window (phase 17 slice 5): the home-screen widget, the
 * media notification, and the artwork System UI loads for it. Nothing here drives the launcher
 * or System UI. The widget is checked through the state store it renders from; the
 * notification through the platform session token it carries, read with a platform
 * [MediaController] the way System UI reads it; the artwork through the Uri grant System UI
 * needs to load the cover from this app's provider.
 *
 * Repeat one is set before anything plays, so a 4 second fixture track loops instead of moving
 * the queue on under a test. Next and Previous still move under repeat one.
 */
class SystemSurfaceTests : E2ETest(FixtureProfile.PairedLibrary) {
    private val widgetStore by lazy { WidgetStateStore(app) }

    @Before
    fun loopTheCurrentTrack() = runBlocking { graph.queueController.setRepeat(RepeatMode.One) }

    @Test
    fun theWidgetFollowsPlayPauseAndTrackChanges() {
        playTrack(BREAKWATER, FIRST)
        awaitWidget("'$FIRST' playing with its cover") {
            it.hasContent &&
                it.title == FIRST &&
                it.subtitle == ARTIST &&
                it.isPlaying &&
                !it.isPodcast &&
                it.artworkUri != null &&
                it.artworkUri == player.current?.artworkUri
        }

        MiniPlayer(ui).playPause()
        awaitWidget("'$FIRST' paused") { it.title == FIRST && !it.isPlaying }

        MiniPlayer(ui).open()
        NowPlayingScreen(ui).next()
        awaitWidget("'$SECOND' after Next") { it.title == SECOND && it.artworkUri == player.current?.artworkUri }
    }

    @Test
    fun theWidgetShowsNoCoverForATrackWithoutOneAndMarksEpisodes() {
        playTrack(SIGNAL, SIGNAL_TRACK)
        awaitWidget("'$SIGNAL_TRACK' playing with no cover") { it.title == SIGNAL_TRACK && it.isPlaying && it.artworkUri == null }

        val podcasts = PodcastsScreen(ui)
        podcasts.open()
        podcasts.openShow(SHOW)
        ShowScreen(ui).playEpisode(EPISODE)
        awaitWidget("'$EPISODE' playing as an episode") { it.title == EPISODE && it.isPodcast && it.isPlaying }
    }

    @Test
    fun clearingTheQueueEmptiesTheWidgetAndRemovesTheNotification() {
        playTrack(BREAKWATER, FIRST)
        awaitWidget("'$FIRST' playing") { it.title == FIRST && it.isPlaying }
        awaitSession("the media notification for '$FIRST'") { it.title == FIRST }

        runBlocking { graph.queueController.clear() }
        awaitWidget("the empty state after the queue is cleared") { it == WidgetState.EMPTY }
        awaitState("the media notification removed after the queue is cleared") { mediaNotification() == null }
    }

    @Test
    fun theNotificationSessionCarriesWhatIsPlaying() {
        playTrack(BREAKWATER, FIRST)
        // The decoded length (the app's own seek bar), not the manifest's: an MP3's encoder padding moves it a little.
        awaitPlayer("'$FIRST' with a known length") { it.durationMs > 0 }
        val durationMs = player.durationMs
        awaitSession("'$FIRST' playing, by $ARTIST on $BREAKWATER, $durationMs ms long like the app's seek bar") {
            it.title == FIRST &&
                it.artist == ARTIST &&
                it.album == BREAKWATER &&
                it.durationMs == durationMs &&
                it.state == PlaybackState.STATE_PLAYING
        }

        MiniPlayer(ui).playPause()
        awaitSession("'$FIRST' paused, the notification still posted") { it.title == FIRST && it.state == PlaybackState.STATE_PAUSED }

        MiniPlayer(ui).open()
        NowPlayingScreen(ui).next()
        awaitSession("'$SECOND' after Next") { it.title == SECOND }
    }

    @Test
    fun theNotificationCoverFollowsTheTrack() {
        playTrack(BREAKWATER, FIRST)
        awaitState("the notification showing the cover of '$FIRST'") {
            val notification = mediaNotification()
            notification?.session()?.title == FIRST && notification.getLargeIcon() != null
        }

        ui.tap(E2ETags.Common.BACK)
        playTrack(SIGNAL, SIGNAL_TRACK)
        awaitState("the notification dropping the old cover for '$SIGNAL_TRACK', which has none") {
            val notification = mediaNotification()
            notification?.session()?.title == SIGNAL_TRACK && notification.getLargeIcon() == null
        }
    }

    @Test
    fun systemUiMayReadTheArtworkTheSessionPublishes() {
        playTrack(BREAKWATER, FIRST)
        awaitSession("'$FIRST' with an artwork Uri") { it.title == FIRST && it.artworkUri != null }
        val artwork = assertNotNull(mediaNotification()?.session()?.artworkUri)
        assertEquals(player.current?.artworkUri, artwork.toString(), "the session's artwork is not the player's")

        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            readPermission(artwork, SYSTEM_UI),
            "System UI has no read grant on $artwork, so the lock screen and media controls show no cover"
        )
        // The control: a package nobody granted must be refused, or the check above proves nothing.
        assertEquals(PackageManager.PERMISSION_DENIED, readPermission(artwork, SHELL), "the shell can read $artwork without a grant")
    }

    private fun playTrack(album: String, title: String) {
        val library = LibraryScreen(ui)
        library.open()
        library.albumsTab()
        library.openAlbum(album)
        AlbumScreen(ui).playTrack(title)
        awaitPlayer("'$title' playing") { it.current?.title == title && it.isPlaying }
    }

    private fun awaitWidget(what: String, condition: (WidgetState) -> Boolean) {
        var last: WidgetState? = null
        try {
            awaitState("the widget state: $what") { widgetStore.read().also { last = it }.let(condition) }
        } catch (failed: AssertionError) {
            throw AssertionError("${failed.message}; last widget state: $last", failed)
        }
    }

    private fun awaitSession(what: String, condition: (SessionView) -> Boolean) {
        var last: SessionView? = null
        try {
            awaitState("the notification's session: $what") { mediaNotification()?.session()?.also { last = it }?.let(condition) == true }
        } catch (failed: AssertionError) {
            throw AssertionError("${failed.message}; last session seen: $last", failed)
        }
    }

    /** This app's posted notification that carries a media session token, as System UI looks for it. */
    private fun mediaNotification(): Notification? = app.getSystemService(NotificationManager::class.java)
        .activeNotifications
        .map { it.notification }
        .firstOrNull { it.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) }

    /** The session behind a media notification, read through its platform token, never through the app's own objects. */
    private fun Notification.session(): SessionView? {
        val token = BundleCompat.getParcelable(extras, Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java) ?: return null
        val controller = MediaController(app, token)
        val metadata = controller.metadata ?: return null
        val artwork = ARTWORK_KEYS.firstNotNullOfOrNull(metadata::getString)
        return SessionView(
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            artworkUri = artwork?.let(Uri::parse),
            state = controller.playbackState?.state
        )
    }

    /** Whether [packageName]'s app may read [uri], as the activity manager answers it for that app's uid. */
    private fun readPermission(uri: Uri, packageName: String): Int {
        val uid = app.packageManager.getPackageUid(packageName, 0)
        check(uid != Process.myUid()) { "$packageName shares this app's uid, so it proves nothing" }
        return app.checkUriPermission(uri, NO_PID, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** The parts of a platform session a test reads. */
    private data class SessionView(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long,
        val artworkUri: Uri?,
        val state: Int?
    )

    private companion object {
        const val BREAKWATER = "Breakwater"
        const val ARTIST = "Dunmore Tide"
        const val FIRST = "Breakwater 1"
        const val SECOND = "Breakwater 2"

        // The one fixture album generated without a cover.
        const val SIGNAL = "Signal"
        const val SIGNAL_TRACK = "Signal 1"

        const val SHOW = "Tidewater Talks"
        const val EPISODE = "Fixture Episode One"

        const val SYSTEM_UI = "com.android.systemui"
        const val SHELL = "com.android.shell"
        const val NO_PID = -1

        // Media3 may publish the cover under any of these platform keys; System UI reads them all.
        val ARTWORK_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
        )
    }
}
