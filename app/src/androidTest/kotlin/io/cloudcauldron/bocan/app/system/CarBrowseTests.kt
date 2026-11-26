package io.cloudcauldron.bocan.app.system

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import androidx.test.platform.app.InstrumentationRegistry
import io.cloudcauldron.bocan.app.E2ETest
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.playback.PlaybackService
import io.cloudcauldron.bocan.playback.browse.MediaTree
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The car's path into the app, without a car: a platform [MediaBrowser] reads the tree and a
 * platform [MediaController] sends `playFromMediaId`, the two APIs Android Auto uses. This pins
 * what a tap puts in the queue. How the head unit draws it still needs the DHU.
 *
 * Repeat one is set first, so a 4 second fixture track cannot move the queue under a test.
 */
class CarBrowseTests : E2ETest(FixtureProfile.PairedLibrary) {
    private lateinit var browser: MediaBrowser
    private lateinit var car: MediaController

    @Before
    fun connectLikeTheCar() {
        runBlocking { graph.queueController.setRepeat(RepeatMode.One) }
        val connected = CompletableDeferred<Unit>()
        onMain {
            browser = MediaBrowser(
                app,
                ComponentName(app, PlaybackService::class.java),
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        connected.complete(Unit)
                    }
                },
                null
            )
            browser.connect()
        }
        runBlocking { withTimeout(BROWSE_TIMEOUT_MS) { connected.await() } }
        onMain { car = MediaController(app, browser.sessionToken) }
    }

    @After
    fun disconnect() = onMain { browser.disconnect() }

    @Test
    fun aTapOnASongQueuesItsAlbumAndOffersNext() {
        val rows = albumRows()
        assertTrue(rows.size >= MIN_ROWS, "the fixture album needs $MIN_ROWS songs, found ${rows.size}")

        car.transportControls.playFromMediaId(rows[1].mediaId, null)
        awaitCar("the second song playing with the whole album queued") {
            nowPlaying() == rows[1].title() && car.queue?.size == rows.size
        }
        awaitCar("the Next action offered") { car.playbackState?.actions?.and(PlaybackState.ACTION_SKIP_TO_NEXT) != 0L }

        car.transportControls.skipToNext()
        awaitCar("Next moving to the third song") { nowPlaying() == rows[2].title() }
    }

    @Test
    fun aSongFromSearchPlaysItsAlbum() {
        val rows = albumRows()
        // The platform browser has no search call, so the row comes from the session's own search.
        val results = runBlocking { app.playbackComponents.browseSearch.search(rows[1].title()) }
        val hit = results.first { it.mediaMetadata.isPlayable == true && it.mediaMetadata.title.toString() == rows[1].title() }

        car.transportControls.playFromMediaId(hit.mediaId, null)
        awaitCar("the search hit playing with its album queued") {
            nowPlaying() == rows[1].title() && car.queue?.size == rows.size
        }
    }

    private fun albumRows(): List<MediaBrowser.MediaItem> {
        val album = children("${MediaTree.PREFIX_ALBUM_BUCKET}F").single { it.title() == ALBUM }
        return children(requireNotNull(album.mediaId))
    }

    private fun children(parentId: String): List<MediaBrowser.MediaItem> = runBlocking {
        val rows = CompletableDeferred<List<MediaBrowser.MediaItem>>()
        val callback = object : MediaBrowser.SubscriptionCallback() {
            override fun onChildrenLoaded(id: String, children: MutableList<MediaBrowser.MediaItem>) {
                rows.complete(children.toList())
            }

            override fun onError(id: String) {
                rows.completeExceptionally(AssertionError("the tree has no children for $id"))
            }
        }
        onMain { browser.subscribe(parentId, callback) }
        withTimeout(BROWSE_TIMEOUT_MS) { rows.await() }.also { onMain { browser.unsubscribe(parentId, callback) } }
    }

    private fun nowPlaying(): String? = car.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)

    private fun MediaBrowser.MediaItem.title(): String = description.title.toString()

    private fun awaitCar(what: String, condition: () -> Boolean) = awaitState("the car sees $what", condition = condition)

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private companion object {
        const val ALBUM = "First Light"
        const val MIN_ROWS = 3
        const val BROWSE_TIMEOUT_MS = 10_000L
    }
}
