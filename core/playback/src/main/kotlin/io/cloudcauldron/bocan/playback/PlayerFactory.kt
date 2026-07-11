package io.cloudcauldron.bocan.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import io.cloudcauldron.bocan.playback.audio.EffectsChain

/**
 * Constructs the single [ExoPlayer] the service owns. The construction is the whole
 * audio contract in one place:
 *
 *  - [DefaultRenderersFactory] in EXTENSION_RENDERER_MODE_PREFER, so the FFmpeg
 *    audio renderer (loaded reflectively when the extension is on the classpath)
 *    is preferred for formats the platform decoders cannot handle (APE, WavPack).
 *  - A [DefaultAudioSink] with the [EffectsChain] processors inserted ahead of the
 *    default silence-skipping and Sonic (speed and pitch) processors, in the fixed
 *    order EQ, bass boost, ReplayGain gain, limiter guard, so gain is applied inside
 *    the shaped signal and speed changes still preserve pitch.
 *  - Music audio attributes with audio focus handled by the player, becoming-noisy
 *    handling on (pause when headphones unplug), and a local wake lock while playing.
 *
 * The [effectsChain] and [ExoPlayer.getAudioSessionId] are the seams the service binds
 * the EQ, effects, and per-item ReplayGain factor to.
 */
@UnstableApi
class PlayerFactory(private val context: Context, val effectsChain: EffectsChain) {
    fun create(): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setAudioProcessors(effectsChain.audioProcessors())
                    .build()
            // enableAudioTrackPlaybackParams is intentionally left at its default
            // (off): speed and pitch are handled in software by the Sonic processor
            // in the sink's chain, which preserves pitch across 0.5x to 2.0x. The
            // AudioTrack hardware path is deprecated and would not preserve pitch.
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // The second argument enables the player's own audio focus handling.
        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }
}
