package io.cloudcauldron.bocan.persistence.model

/** Whether the bytes for a synced track or episode are present on this device. */
enum class DownloadState(val wire: String) {
    Pending("pending"),
    Downloaded("downloaded"),
    Failed("failed")
    ;

    companion object {
        fun fromWire(value: String): DownloadState =
            entries.firstOrNull { it.wire == value } ?: error("Unknown DownloadState wire value: $value")
    }
}
