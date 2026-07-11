package io.cloudcauldron.bocan.playback.audio

import kotlinx.serialization.Serializable

/**
 * Which ReplayGain values, if any, the [ReplayGainProcessor] applies.
 *
 * - [Off]: no gain, the processor is a pass-through.
 * - [Track]: per-track gain, so every track plays at a uniform loudness.
 * - [Album]: per-album gain, preserving the intended loudness relationships
 *   between tracks on one album (the right default for album listening).
 *
 * Serialisable so it can live in the persisted [EqState].
 */
@Serializable
enum class ReplayGainMode {
    Off,
    Track,
    Album
}
