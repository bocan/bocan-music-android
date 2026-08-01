package io.cloudcauldron.bocan.playback.queue

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression cover for the wrong-next-track-during-shuffle bug: an external
 * controller (Android Auto's shuffle toggle, a car's Bluetooth shuffle button)
 * could enable ExoPlayer's built-in shuffle order underneath the app's
 * queue-reorder shuffle, leaving two orders fighting over what plays next. The
 * wrapper must translate that command into a queue reorder and never let the
 * raw flag flip.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ReorderingShufflePlayerTests {
    private lateinit var raw: ExoPlayer
    private lateinit var wrapper: ReorderingShufflePlayer

    @Before
    fun setUp() {
        raw = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        wrapper = ReorderingShufflePlayer(raw) { Random(SEED) }
    }

    @After
    fun tearDown() {
        wrapper.release()
        raw.release()
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun items(count: Int): List<MediaItem> =
        (1..count).map { MediaItem.Builder().setMediaId("t$it").setUri("file:///tracks/t$it.flac").build() }

    private fun queueIds(): List<String> = (0 until raw.mediaItemCount).map { raw.getMediaItemAt(it).mediaId }

    @Test
    fun `shuffle on reorders the upcoming queue and never the raw flag`() {
        raw.setMediaItems(items(10))
        idle()
        val original = queueIds()

        wrapper.shuffleModeEnabled = true
        idle()

        assertTrue(wrapper.shuffleModeEnabled)
        assertFalse(raw.shuffleModeEnabled)
        assertEquals("t1", queueIds().first()) // the current item stays put
        assertEquals(original.toSet(), queueIds().toSet()) // nothing lost or duplicated
        assertNotEquals(original, queueIds()) // the upcoming items are reordered
    }

    @Test
    fun `shuffle off keeps the queue order and lowers the flag`() {
        raw.setMediaItems(items(10))
        idle()
        wrapper.shuffleModeEnabled = true
        idle()
        val shuffled = queueIds()

        wrapper.shuffleModeEnabled = false
        idle()

        assertFalse(wrapper.shuffleModeEnabled)
        assertEquals(shuffled, queueIds())
    }

    @Test
    fun `repeating shuffle on does not keep reshuffling`() {
        raw.setMediaItems(items(10))
        idle()
        wrapper.shuffleModeEnabled = true
        idle()
        val afterFirst = queueIds()

        wrapper.shuffleModeEnabled = true
        idle()

        assertEquals(afterFirst, queueIds())
    }

    @Test
    fun `restoring the flag does not reorder the restored queue`() {
        raw.setMediaItems(items(10))
        idle()
        val original = queueIds()

        wrapper.restoreShuffleFlag(true)
        idle()

        assertTrue(wrapper.shuffleModeEnabled)
        assertFalse(raw.shuffleModeEnabled)
        assertEquals(original, queueIds())
    }

    @Test
    fun `listeners hear the flag change so every surface stays in sync`() {
        raw.setMediaItems(items(3))
        idle()
        val heard = mutableListOf<Boolean>()
        wrapper.addListener(
            object : Player.Listener {
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    heard += shuffleModeEnabled
                }
            }
        )

        wrapper.shuffleModeEnabled = true
        idle()
        wrapper.shuffleModeEnabled = false
        idle()

        assertEquals(listOf(true, false), heard)
    }

    @Test
    fun `shuffle on with an empty or single-item queue is safe`() {
        wrapper.shuffleModeEnabled = true
        idle()
        assertTrue(wrapper.shuffleModeEnabled)

        raw.setMediaItems(items(1))
        idle()
        wrapper.shuffleModeEnabled = false
        idle()
        wrapper.shuffleModeEnabled = true
        idle()
        assertEquals(listOf("t1"), queueIds())
    }

    private companion object {
        // Any fixed seed whose permutation of ten items is not the identity.
        const val SEED = 42
    }
}
