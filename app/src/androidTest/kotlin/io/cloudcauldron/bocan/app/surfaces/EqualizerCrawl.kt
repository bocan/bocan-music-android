package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.surfaces.Action.Slide
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Action.Type
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Flipped
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Gone
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Holds
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Selected
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Text
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import io.cloudcauldron.bocan.playback.audio.EqBands
import io.cloudcauldron.bocan.playback.audio.EqState
import kotlin.math.roundToInt
import org.junit.Test

/**
 * The equalizer: the master switch, every built-in preset, every band slider, bass boost,
 * each ReplayGain mode with the preamp, skip silence, the fade, and a user preset saved
 * (after one cancelled save) and deleted.
 */
class EqualizerCrawl : CrawlTest(FixtureProfile.PairedLibrary) {
    @Test
    fun crawl() {
        crawl("equalizer", table(ctx))
        finishCrawl()
    }

    companion object {
        private const val USER_PRESET = "Crawl curve"
        private const val RAISED = 0.75f
        private const val TOP = 1f
        private const val HZ_PER_KHZ = 1000.0

        // The built-in presets' display names, in PresetPicker's order (BUILT_IN_NAMES there).
        private val BUILT_IN_NAMES = listOf(
            R.string.eq_preset_flat,
            R.string.eq_preset_rock,
            R.string.eq_preset_jazz,
            R.string.eq_preset_classical,
            R.string.eq_preset_electronic,
            R.string.eq_preset_vocal_boost,
            R.string.eq_preset_bass_boost,
            R.string.eq_preset_treble_boost,
            R.string.eq_preset_loudness,
            R.string.eq_preset_spoken_word
        )

        fun table(c: CrawlContext): List<Step> = open() + presets(c) + sliders(c) + userPreset()

        private fun eq(c: CrawlContext): EqState = c.now(c.graph.eqPreferences.state)

        private fun open() = listOf(
            Step(E2ETags.Nav.SETTINGS, Tap, Navigated("settings", E2ETags.Settings.SYNC)),
            Step(E2ETags.Settings.PLAYBACK, Tap, Navigated("playback settings", E2ETags.PlaybackSettings.EQUALIZER)),
            Step(E2ETags.PlaybackSettings.EQUALIZER, Tap, Navigated("equalizer", E2ETags.Equalizer.MASTER)),
            Step(E2ETags.Equalizer.MASTER, Tap, Flipped)
        )

        private fun presets(c: CrawlContext) = BUILT_IN_NAMES.map(c::string).map { name ->
            Step(E2ETags.Equalizer.PRESET_OPTION, Tap, Selected(E2ETags.Equalizer.PRESET_OPTION, name), row = name)
        }

        private fun sliders(c: CrawlContext): List<Step> {
            val bands = EqBands.centersHz.mapIndexed { index, hz ->
                Step(
                    E2ETags.Equalizer.BAND,
                    Slide(RAISED),
                    Holds("band ${index + 1} raised above 0 dB") { eq(c).bandGainsDb[index] > 0.0 },
                    row = spokenFrequency(c, hz)
                )
            }
            return bands + listOf(
                Step(
                    E2ETags.Equalizer.BASS_BOOST,
                    Slide(TOP),
                    Holds("bass boost at its maximum") { eq(c).bassBoostDb == EqState.BASS_MAX_DB }
                ),
                Step(E2ETags.Equalizer.REPLAYGAIN_TRACK, Tap, Selected(E2ETags.Equalizer.REPLAYGAIN_TRACK)),
                Step(E2ETags.Equalizer.REPLAYGAIN_ALBUM, Tap, Selected(E2ETags.Equalizer.REPLAYGAIN_ALBUM)),
                Step(E2ETags.Equalizer.PREAMP, Slide(TOP), Holds("preamp at its maximum") { eq(c).preampDb == EqState.PREAMP_MAX_DB }),
                Step(E2ETags.Equalizer.REPLAYGAIN_OFF, Tap, Gone(E2ETags.Equalizer.PREAMP)),
                Step(E2ETags.Equalizer.SKIP_SILENCE, Tap, Flipped),
                Step(E2ETags.Equalizer.FADE, Slide(TOP), Holds("fade at its maximum") { eq(c).fadeSeconds == EqState.FADE_MAX_SECONDS })
            )
        }

        private fun userPreset() = listOf(
            Step(E2ETags.Equalizer.SAVE_PRESET, Tap, Visible(E2ETags.Equalizer.SAVE_NAME)),
            Step(E2ETags.Equalizer.SAVE_NAME, Type(USER_PRESET), Text(E2ETags.Equalizer.SAVE_NAME, USER_PRESET)),
            Step(E2ETags.Equalizer.SAVE_CANCEL, Tap, Gone(E2ETags.Equalizer.SAVE_NAME)),
            Step(E2ETags.Equalizer.SAVE_PRESET, Tap, Visible(E2ETags.Equalizer.SAVE_NAME)),
            Step(E2ETags.Equalizer.SAVE_NAME, Type(USER_PRESET), Text(E2ETags.Equalizer.SAVE_NAME, USER_PRESET)),
            Step(E2ETags.Equalizer.SAVE_CONFIRM, Tap, Visible(E2ETags.Equalizer.PRESET_OPTION, USER_PRESET)),
            Step(E2ETags.Equalizer.PRESET_DELETE, Tap, Gone(E2ETags.Equalizer.PRESET_OPTION, USER_PRESET), row = USER_PRESET)
        )

        /** What TalkBack reads for a band, as BandSlider builds it: "1 kilohertz", "63 hertz". */
        private fun spokenFrequency(c: CrawlContext, hz: Double): String = if (hz >= HZ_PER_KHZ) {
            c.string(R.string.eq_kilohertz_spoken, (hz / HZ_PER_KHZ).roundToInt())
        } else {
            c.string(R.string.eq_hertz_spoken, hz.roundToInt())
        }
    }
}
