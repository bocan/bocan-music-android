package io.cloudcauldron.bocan.playback.audio

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.Test

/** The preset catalogue, the [EqState] reducers, and JSON round-tripping, all pure. */
class EqSettingsTests {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `there are ten bands and ten built-in presets`() {
        assertEquals(10, EqBands.COUNT)
        assertEquals(10, EqBands.centersHz.size)
        assertEquals(10, BuiltInPresets.all.size)
    }

    @Test
    fun `every preset carries exactly ten gains and flat is flat`() {
        BuiltInPresets.all.forEach { assertEquals(EqBands.COUNT, it.bandGainsDb.size) }
        assertTrue(BuiltInPresets.flat.isFlat)
        assertFalse(BuiltInPresets.rock.isFlat)
    }

    @Test
    fun `preset names match the Mac`() {
        val expected = listOf(
            "Flat", "Rock", "Jazz", "Classical", "Electronic",
            "Vocal Boost", "Bass Boost", "Treble Boost", "Loudness", "Spoken Word"
        )
        assertEquals(expected, BuiltInPresets.all.map { it.name })
    }

    @Test
    fun `snap clamps to range and the half decibel grid`() {
        assertEquals(12.0, EqBands.snap(20.0), 0.0)
        assertEquals(-12.0, EqBands.snap(-99.0), 0.0)
        assertEquals(3.0, EqBands.snap(3.2), 0.0)
        assertEquals(3.5, EqBands.snap(3.3), 0.0)
    }

    @Test
    fun `applying a preset adopts its curve and marks it active`() {
        val state = EqState().withPreset(BuiltInPresets.rock)
        assertEquals(BuiltInPresets.rock.bandGainsDb, state.bandGainsDb)
        assertEquals(BuiltInPresets.rock.id, state.activePresetId)
    }

    @Test
    fun `editing a band clears the active preset unless the curve matches one`() {
        val custom = EqState().withPreset(BuiltInPresets.rock).withBand(0, 9.0)
        assertNull(custom.activePresetId)
        assertEquals(9.0, custom.bandGainsDb[0], 0.0)

        val backToFlat = EqState().withPreset(BuiltInPresets.rock).let { rock ->
            BuiltInPresets.rock.bandGainsDb.foldIndexed(rock) { i, acc, _ -> acc.withBand(i, 0.0) }
        }
        assertEquals(BuiltInPresets.FLAT_ID, backToFlat.activePresetId)
    }

    @Test
    fun `saving and deleting a user preset round-trips`() {
        val saved = EqState().withBand(5, 4.0).savingUserPreset("uid-1", "My Curve")
        assertEquals("uid-1", saved.activePresetId)
        assertEquals(1, saved.userPresets.size)
        assertEquals("My Curve", saved.userPresets.single().name)
        assertFalse(saved.userPresets.single().isBuiltIn)

        val deleted = saved.deletingUserPreset("uid-1")
        assertTrue(deleted.userPresets.isEmpty())
        assertNull(deleted.activePresetId)
    }

    @Test
    fun `allBandsNonPositive tracks boosts and bass`() {
        assertTrue(EqState().allBandsNonPositive)
        assertFalse(EqState().withBand(3, 2.0).allBandsNonPositive)
        assertFalse(EqState().copy(bassBoostDb = 3.0).allBandsNonPositive)
    }

    @Test
    fun `state round-trips through JSON with user presets`() {
        val original = EqState(
            enabled = true,
            replayGainMode = ReplayGainMode.Album,
            preampDb = -2.5,
            bassBoostDb = 4.0,
            skipSilence = true,
            fadeSeconds = 6
        ).savingUserPreset("uid-9", "Night")
        val restored = json.decodeFromString(EqState.serializer(), json.encodeToString(EqState.serializer(), original))
        assertEquals(original, restored)
    }

    @Test
    fun `defaults are the honest off state`() {
        val state = EqState()
        assertFalse(state.enabled)
        assertEquals(ReplayGainMode.Off, state.replayGainMode)
        assertEquals(0, state.fadeSeconds)
        assertFalse(state.skipSilence)
        assertEquals(EqBands.flatGains, state.bandGainsDb)
    }
}
