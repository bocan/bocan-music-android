package io.cloudcauldron.bocan.playback.audio

import kotlinx.serialization.Serializable

/**
 * The ten graphic-EQ bands. Frequencies match the Mac player's bands (see
 * `bocan-music` AudioEngine): band index i is the same band on both platforms, so a
 * preset's curve maps straight across by position. The Mac labels its bands with the
 * ISO 1/3-octave centres (31.5, 63, ...); the values here are the phase 08 contract's
 * exact figures, which sit on the same band positions.
 */
object EqBands {
    /** Band centre frequencies in Hz, low to high. */
    val centersHz: List<Double> = listOf(31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

    /** Number of bands; a preset carries exactly this many gains. */
    const val COUNT: Int = 10

    /** Per-band gain range and step, in decibels. */
    const val MIN_DB: Double = -12.0
    const val MAX_DB: Double = 12.0
    const val STEP_DB: Double = 0.5

    /** A flat curve: unity gain on every band. */
    val flatGains: List<Double> = List(COUNT) { 0.0 }

    /** Snap [db] to the allowed range and 0.5 dB grid. */
    fun snap(db: Double): Double = (Math.round(db / STEP_DB) * STEP_DB).coerceIn(MIN_DB, MAX_DB)
}

/**
 * A named ten-band preset: a curve of per-band gains in decibels. Built-in presets use
 * stable `bocan.*` ids and are ported verbatim from the Mac's [BuiltInPresets]; user
 * presets carry a generated id and [isBuiltIn] = false. Serialised into DataStore as
 * part of [EqState].
 */
@Serializable
data class EqPreset(val id: String, val name: String, val bandGainsDb: List<Double>, val isBuiltIn: Boolean) {
    init {
        require(bandGainsDb.size == EqBands.COUNT) { "an EqPreset needs exactly ${EqBands.COUNT} band gains" }
    }

    /** True when every band is at 0 dB, so the EQ can be bypassed entirely. */
    val isFlat: Boolean get() = bandGainsDb.all { it == 0.0 }
}

/**
 * The Mac's built-in presets, names and curves copied exactly from
 * `bocan-music/Modules/AudioEngine/Sources/AudioEngine/DSP/Presets/BuiltInPresets.swift`
 * so both platforms voice a preset identically. The phase 08 spec's inline preset list
 * was an approximation; the acceptance criterion binds these to the Mac's names, so the
 * Mac is authoritative here.
 */
object BuiltInPresets {
    const val FLAT_ID = "bocan.flat"

    val flat = EqPreset(FLAT_ID, "Flat", listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), isBuiltIn = true)
    val rock = EqPreset("bocan.rock", "Rock", listOf(4.0, 3.0, -2.0, -3.0, -1.0, 2.0, 4.0, 5.0, 5.0, 4.0), isBuiltIn = true)
    val jazz = EqPreset("bocan.jazz", "Jazz", listOf(2.0, 2.0, 1.0, 0.0, -1.0, -1.0, 0.0, 1.0, 2.0, 3.0), isBuiltIn = true)
    val classical =
        EqPreset("bocan.classical", "Classical", listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -2.0, -2.0, -2.0, -3.0), isBuiltIn = true)
    val electronic =
        EqPreset("bocan.electronic", "Electronic", listOf(5.0, 4.0, 1.0, 0.0, -2.0, 2.0, 1.0, 1.0, 4.0, 5.0), isBuiltIn = true)
    val vocalBoost =
        EqPreset("bocan.vocal_boost", "Vocal Boost", listOf(-2.0, -2.0, -1.0, 1.0, 3.0, 3.0, 2.0, 1.0, 0.0, -1.0), isBuiltIn = true)
    val bassBoost =
        EqPreset("bocan.bass_boost", "Bass Boost", listOf(6.0, 5.0, 3.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), isBuiltIn = true)
    val trebleBoost =
        EqPreset("bocan.treble_boost", "Treble Boost", listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 3.0, 5.0, 6.0, 6.0), isBuiltIn = true)
    val loudness =
        EqPreset("bocan.loudness", "Loudness", listOf(6.0, 4.0, 1.0, 0.0, -2.0, -1.0, 0.0, 1.0, 4.0, 6.0), isBuiltIn = true)
    val spokenWord =
        EqPreset("bocan.spoken_word", "Spoken Word", listOf(-4.0, -3.0, 0.0, 2.0, 4.0, 3.0, 2.0, 1.0, -1.0, -2.0), isBuiltIn = true)

    /** All built-in presets in the Mac's display order (Flat first). */
    val all: List<EqPreset> = listOf(flat, rock, jazz, classical, electronic, vocalBoost, bassBoost, trebleBoost, loudness, spokenWord)

    /** The preset with [id], or null if unknown. */
    fun byId(id: String?): EqPreset? = all.firstOrNull { it.id == id }
}

/**
 * The full, serialisable effects state the [EffectsChain] applies and the settings UI
 * edits. Persisted as one JSON blob in DataStore. Defaults are the honest, off state:
 * the EQ disabled and flat, no bass boost, ReplayGain off, gapless preserved (fades off),
 * skip silence off. Bass boost is 0 to +9 dB (phase 08 contract); the preamp is the
 * ReplayGain offset in -6 to +6 dB.
 */
@Serializable
data class EqState(
    val enabled: Boolean = false,
    val activePresetId: String? = BuiltInPresets.FLAT_ID,
    val bandGainsDb: List<Double> = EqBands.flatGains,
    val bassBoostDb: Double = 0.0,
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    val preampDb: Double = 0.0,
    val skipSilence: Boolean = false,
    val fadeSeconds: Int = 0,
    val userPresets: List<EqPreset> = emptyList()
) {
    /** True when no band is boosted and the bass shelf is flat: nothing can push past full scale. */
    val allBandsNonPositive: Boolean get() = bandGainsDb.all { it <= 0.0 } && bassBoostDb <= 0.0

    /** Built-in plus user presets, built-ins first, for the picker. */
    val allPresets: List<EqPreset> get() = BuiltInPresets.all + userPresets

    /** Adopt [preset]'s curve and mark it active. */
    fun withPreset(preset: EqPreset): EqState = copy(activePresetId = preset.id, bandGainsDb = preset.bandGainsDb)

    /** Set one band, snapping to the grid, and clear the active preset (the curve is now custom). */
    fun withBand(index: Int, db: Double): EqState {
        val gains = bandGainsDb.toMutableList().also { it[index] = EqBands.snap(db) }
        val match = allPresets.firstOrNull { it.bandGainsDb == gains }
        return copy(bandGainsDb = gains, activePresetId = match?.id)
    }

    /** Add a user preset capturing the current curve under [name], and make it active. */
    fun savingUserPreset(id: String, name: String): EqState {
        val preset = EqPreset(id, name, bandGainsDb, isBuiltIn = false)
        return copy(userPresets = userPresets + preset, activePresetId = id)
    }

    /** Remove the user preset with [id]; a no-op for built-ins. Clears the active id if it was that preset. */
    fun deletingUserPreset(id: String): EqState = copy(
        userPresets = userPresets.filterNot { it.id == id },
        activePresetId = if (activePresetId == id) null else activePresetId
    )

    companion object {
        /** Bass boost range, in decibels (phase 08 contract). */
        const val BASS_MIN_DB: Double = 0.0
        const val BASS_MAX_DB: Double = 9.0

        /** ReplayGain preamp range, in decibels. */
        const val PREAMP_MIN_DB: Double = -6.0
        const val PREAMP_MAX_DB: Double = 6.0

        /** Fade-between-tracks range, in seconds (0 = off, gapless preserved). */
        const val FADE_MIN_SECONDS: Int = 0
        const val FADE_MAX_SECONDS: Int = 12
    }
}
