package io.cloudcauldron.bocan.persistence

import androidx.room3.ColumnTypeConverter
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.model.LyricsKind
import io.cloudcauldron.bocan.persistence.model.PlayState
import io.cloudcauldron.bocan.persistence.model.PlaylistKind
import java.time.Instant

/**
 * Column converters: enums as their wire strings (not Kotlin names, so the DB
 * matches the protocol vocabulary), Instant as epoch millis.
 */
class Converters {
    @ColumnTypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @ColumnTypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @ColumnTypeConverter
    fun fromDownloadState(value: DownloadState): String = value.wire

    @ColumnTypeConverter
    fun toDownloadState(value: String): DownloadState = DownloadState.fromWire(value)

    @ColumnTypeConverter
    fun fromPlayState(value: PlayState): String = value.wire

    @ColumnTypeConverter
    fun toPlayState(value: String): PlayState = PlayState.fromWire(value)

    @ColumnTypeConverter
    fun fromPlaylistKind(value: PlaylistKind): String = value.wire

    @ColumnTypeConverter
    fun toPlaylistKind(value: String): PlaylistKind = PlaylistKind.fromWire(value)

    @ColumnTypeConverter
    fun fromLyricsKind(value: LyricsKind): String = value.wire

    @ColumnTypeConverter
    fun toLyricsKind(value: String): LyricsKind = LyricsKind.fromWire(value)
}
