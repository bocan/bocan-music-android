package io.cloudcauldron.bocan.persistence.model

/** Playlist flavour as delivered by the manifest. Smart lists arrive pre-evaluated. */
enum class PlaylistKind(val wire: String) {
    Manual("manual"),
    Smart("smart"),
    Folder("folder")
    ;

    companion object {
        fun fromWire(value: String): PlaylistKind = fromWireOrNull(value) ?: error("Unknown PlaylistKind wire value: $value")

        fun fromWireOrNull(value: String): PlaylistKind? = entries.firstOrNull { it.wire == value }
    }
}
