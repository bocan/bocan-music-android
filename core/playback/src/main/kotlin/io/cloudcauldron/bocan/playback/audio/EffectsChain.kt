package io.cloudcauldron.bocan.playback.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the effects processor instances and applies the [EqState] to them. The chain is
 * inserted into the ExoPlayer audio sink in one fixed order (phase 08 contract):
 *
 *     decoder output -> EQ -> bass boost -> ReplayGain gain -> limiter guard -> sink
 *
 * See [audioProcessors]. Settings arrive from DataStore on start and on every change via
 * [applySettings]; the processor-side effects (EQ curve, bass shelf, limiter guard, fade
 * window, ReplayGain mode and preamp) apply immediately through volatile swaps, so the
 * master switch A/Bs within a buffer.
 *
 * Two effects need the running player, which only the service holds: silence skipping is
 * a player property, and the per-item ReplayGain factor depends on the current item's
 * tag. The service calls [bind] with those seams; [applySettings] then refreshes them,
 * and [onItemTransition] recomputes the factor as items change.
 */
@UnstableApi
class EffectsChain(
    private val dispatchers: CoroutineDispatchers,
    val eqProcessor: EqProcessor = EqProcessor(),
    val bassBoostProcessor: BassBoostProcessor = BassBoostProcessor(),
    val replayGainProcessor: ReplayGainProcessor = ReplayGainProcessor(),
    val limiterProcessor: LimiterProcessor = LimiterProcessor(),
    val waveformTap: WaveformTap = WaveformTap()
) {
    /** The fade gain stage is the ReplayGain processor: fades and ReplayGain compose there. */
    val crossfader = Crossfader({ gain -> replayGainProcessor.setFadeFactor(gain) }, dispatchers)

    @Volatile
    private var mode: ReplayGainMode = ReplayGainMode.Off

    @Volatile
    private var preampDb: Double = 0.0

    @Volatile
    private var skipSilenceOn: Boolean = false

    @Volatile
    private var binding: Binding? = null

    /** The player-held seams the service supplies once its player exists. */
    class Binding(val skipSilence: SkipSilence, val currentItemValues: () -> ReplayGainValues?, val scope: CoroutineScope)

    /**
     * The processors in the fixed chain order for [androidx.media3.exoplayer.audio.DefaultAudioSink].
     * The waveform tap is last, a pass-through that reads the fully shaped signal for the
     * oscilloscope without altering it.
     */
    fun audioProcessors(): Array<AudioProcessor> =
        arrayOf(eqProcessor, bassBoostProcessor, replayGainProcessor, limiterProcessor, waveformTap)

    /** Apply the full effects state: the EQ curve and bass shelf are gated by the master [EqState.enabled]. */
    fun applySettings(state: EqState) {
        eqProcessor.setGains(if (state.enabled) state.bandGainsDb else EqBands.flatGains)
        bassBoostProcessor.setGainDb(if (state.enabled) state.bassBoostDb else EqState.BASS_MIN_DB)
        // The limiter guards only when a positive gain could push past full scale.
        limiterProcessor.setEnabled(state.enabled && !state.allBandsNonPositive)
        mode = state.replayGainMode
        preampDb = state.preampDb
        skipSilenceOn = state.skipSilence
        crossfader.fadeSeconds = state.fadeSeconds
        if (state.fadeSeconds == 0) crossfader.restore()
        refreshPlayerBoundEffects()
    }

    /** Bind the player-held seams and apply the current player-bound settings once. */
    fun bind(binding: Binding) {
        this.binding = binding
        refreshPlayerBoundEffects()
    }

    /** Recompute the ReplayGain factor for a newly current item and clear any leftover fade. */
    fun onItemTransition(values: ReplayGainValues) {
        replayGainProcessor.setFactor(ReplayGainMath.factor(mode, values, preampDb))
        crossfader.restore()
    }

    /** The ReplayGain factor for [values] under the current mode and preamp (pure, for tests). */
    fun replayGainFactor(values: ReplayGainValues): Double = ReplayGainMath.factor(mode, values, preampDb)

    private fun refreshPlayerBoundEffects() {
        val active = binding ?: return
        active.scope.launch {
            withContext(dispatchers.main) {
                active.skipSilence.setEnabled(skipSilenceOn)
                replayGainProcessor.setFactor(ReplayGainMath.factor(mode, active.currentItemValues() ?: ReplayGainValues.NONE, preampDb))
            }
        }
    }
}
