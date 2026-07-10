package io.cloudcauldron.bocan.persistence.model

/** Listening progress for a podcast episode. The phone owns this state. */
enum class PlayState(val wire: String) {
    Unplayed("unplayed"),
    InProgress("inProgress"),
    Played("played")
    ;

    companion object {
        fun fromWire(value: String): PlayState = fromWireOrNull(value) ?: error("Unknown PlayState wire value: $value")

        /** Lenient form for manifest values, which may grow new states in future protocol versions. */
        fun fromWireOrNull(value: String): PlayState? = entries.firstOrNull { it.wire == value }
    }
}
