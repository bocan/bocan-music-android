package io.cloudcauldron.bocan.playback.queue

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.random.Random

/**
 * The player the session exposes to every controller. Shuffle in this app is a
 * queue property (ShuffleStrategy reorders the actual queue), never ExoPlayer's
 * built-in shuffle order: two orders over one queue means "next" depends on which
 * system wins, which is exactly the wrong-next-track bug this class exists to end.
 *
 * Controllers the app does not own (Android Auto's shuffle toggle, a car stereo's
 * Bluetooth AVRCP shuffle button) send the standard set-shuffle command, which
 * would otherwise reach the raw player. This wrapper intercepts it: turning
 * shuffle on reorders the upcoming queue once (uniformly, the same behaviour as
 * the in-app toggle) and raises a reported flag; turning it off lowers the flag
 * and keeps the queue order, matching the Mac. The wrapped player's own shuffle
 * mode is never touched, so ExoPlayer's playback order is always the queue order,
 * for every surface.
 */
@UnstableApi
class ReorderingShufflePlayer(player: Player, private val random: () -> Random = { Random.Default }) :
    ForwardingSimpleBasePlayer(player) {
    private var shuffleFlag = false

    override fun getState(): State = super.getState().buildUpon().setShuffleModeEnabled(shuffleFlag).build()

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        // Reorder only on the off-to-on edge: repeating "on" must not keep reshuffling,
        // and "off" keeps the queue as it stands (only the flag clears, matching the Mac).
        if (shuffleModeEnabled && !shuffleFlag) reorderUpcoming()
        shuffleFlag = shuffleModeEnabled
        return Futures.immediateVoidFuture()
    }

    /**
     * Restore the flag from a persisted snapshot without reordering: the restored
     * queue already carries whatever order the listener last heard.
     */
    fun restoreShuffleFlag(enabled: Boolean) {
        shuffleFlag = enabled
        invalidateState()
    }

    /** Uniformly reorder the items after the current one, keeping the current item playing. */
    private fun reorderUpcoming() {
        val player = player
        val count = player.mediaItemCount
        val currentIndex = player.currentMediaItemIndex
        if (count <= 1 || currentIndex < 0 || currentIndex >= count - 1) return
        val upcoming = ((currentIndex + 1) until count).map(player::getMediaItemAt)
        player.replaceMediaItems(currentIndex + 1, count, upcoming.shuffled(random()))
    }
}
