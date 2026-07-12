package io.cloudcauldron.bocan.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class EffectsChainTests {
    @Test
    fun `the chain exposes its processors in order, tap last`() {
        val chain = EffectsChain(CoroutineDispatchers())
        assertEquals(
            listOf(
                chain.eqProcessor,
                chain.bassBoostProcessor,
                chain.replayGainProcessor,
                chain.limiterProcessor,
                chain.waveformTap
            ),
            chain.audioProcessors().toList()
        )
    }

    @Test
    fun `disabled settings keep the EQ flat and the limiter off`() {
        val chain = configuredChain()
        chain.applySettings(EqState(enabled = false, bandGainsDb = BuiltInPresets.rock.bandGainsDb))
        assertEquals(0, chain.eqProcessor.activeBandCount)
        assertFalse(chain.bassBoostProcessor.isBoosting)
        assertFalse(chain.limiterProcessor.isEnabled)
    }

    @Test
    fun `enabling a boosting preset arms the EQ, bass, and limiter guard`() {
        val chain = configuredChain()
        chain.applySettings(EqState(enabled = true, bandGainsDb = BuiltInPresets.rock.bandGainsDb, bassBoostDb = 4.0))
        assertEquals(EqBands.COUNT, chain.eqProcessor.activeBandCount)
        assertTrue(chain.bassBoostProcessor.isBoosting)
        assertTrue(chain.limiterProcessor.isEnabled)
    }

    @Test
    fun `a non-boosting curve does not arm the limiter`() {
        val chain = configuredChain()
        chain.applySettings(EqState(enabled = true, bandGainsDb = BuiltInPresets.classical.bandGainsDb))
        assertFalse(chain.limiterProcessor.isEnabled, "classical only cuts, so nothing can clip")
    }

    @Test
    fun `an item transition applies the ReplayGain factor for the current mode`() {
        val chain = EffectsChain(CoroutineDispatchers())
        chain.applySettings(EqState(replayGainMode = ReplayGainMode.Track))
        chain.onItemTransition(ReplayGainValues(trackGainDb = -6.0, trackPeak = 0.9, albumGainDb = null, albumPeak = null))
        val expected = ReplayGainMath.factor(ReplayGainMode.Track, ReplayGainValues(-6.0, 0.9, null, null))
        assertEquals(expected, chain.replayGainProcessor.currentFactor, 1e-9)
    }

    @Test
    fun `binding pushes skip silence and refreshes the factor on the main dispatcher`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val chain = EffectsChain(CoroutineDispatchers(io = dispatcher, default = dispatcher, main = dispatcher))
        var skipSilence = false
        val values = ReplayGainValues(trackGainDb = 3.0, trackPeak = null, albumGainDb = null, albumPeak = null)
        chain.bind(EffectsChain.Binding(skipSilence = { skipSilence = it }, currentItemValues = { values }, scope = this))

        chain.applySettings(EqState(skipSilence = true, replayGainMode = ReplayGainMode.Track))
        advanceUntilIdle()

        assertTrue(skipSilence)
        assertEquals(ReplayGainMath.factor(ReplayGainMode.Track, values), chain.replayGainProcessor.currentFactor, 1e-9)
    }

    private fun configuredChain(): EffectsChain {
        val chain = EffectsChain(CoroutineDispatchers())
        val format = AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT)
        chain.eqProcessor.configure(format)
        chain.bassBoostProcessor.configure(format)
        return chain
    }
}
