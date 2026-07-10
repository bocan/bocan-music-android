package io.cloudcauldron.bocan.persistence.model

/** Lyrics document flavour, mirroring the sync protocol's lyrics endpoint. */
enum class LyricsKind(val wire: String) {
    Synced("synced"),
    Unsynced("unsynced")
    ;

    companion object {
        fun fromWire(value: String): LyricsKind = entries.firstOrNull { it.wire == value } ?: error("Unknown LyricsKind wire value: $value")
    }
}
