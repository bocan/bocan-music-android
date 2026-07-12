@file:Suppress("TooManyFunctions")

package io.cloudcauldron.bocan.app.player

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.playback.session.AudioPipelineFormat
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/**
 * The read-only song details sheet. One labelled row per known value; unknown rows are
 * omitted so nothing shows a raw null or 0. Every value goes through a platform formatter,
 * every label is a string resource, and every row merges into one TalkBack sentence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailsSheet(state: SongDetailsUiState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            when (state) {
                is SongDetailsUiState.Track -> TrackDetails(state)
                is SongDetailsUiState.Episode -> EpisodeDetails(state)
                SongDetailsUiState.Loading, SongDetailsUiState.Empty -> Unit
            }
        }
    }
}

@Composable
private fun TrackDetails(track: SongDetailsUiState.Track) {
    val context = LocalContext.current
    DetailsHeading(track.title)
    DetailRow(stringResource(R.string.song_details_artist), track.artist)
    DetailRow(stringResource(R.string.song_details_album), track.album)
    DetailRow(stringResource(R.string.song_details_album_artist), track.albumArtist)
    DetailRow(stringResource(R.string.song_details_year), track.year?.let { stringResource(R.string.song_details_number, it) })
    DetailRow(stringResource(R.string.song_details_genre), track.genre)
    DetailRow(stringResource(R.string.song_details_track), numberOfTotal(track.trackNumber, track.trackTotal))
    DetailRow(stringResource(R.string.song_details_disc), numberOfTotal(track.discNumber, track.discTotal))
    FormatRow(track.format, track.lossless)
    DetailRow(stringResource(R.string.song_details_duration), formatDuration(track.durationMs))
    DetailRow(stringResource(R.string.song_details_size), formatSize(context, track.sizeBytes))
    val bitrate = track.bitrateKbps?.let { stringResource(R.string.song_details_bitrate_value, formatInteger(it)) }
    DetailRow(stringResource(R.string.song_details_bitrate), bitrate)
    DetailRow(stringResource(R.string.song_details_plays), track.playCount?.let { formatInteger(it) })
    val lastPlayed = track.lastPlayedAt?.let { formatRelative(it.toEpochMilli()) }
    DetailRow(stringResource(R.string.song_details_last_played), lastPlayed)
    if (track.loved) DetailRow(stringResource(R.string.song_details_loved), stringResource(R.string.song_details_loved_yes))
    if (track.rating > 0) {
        val rating = stringResource(R.string.song_details_rating_value, track.rating, MAX_RATING)
        DetailRow(stringResource(R.string.song_details_rating), rating)
    }
    DetailRow(stringResource(R.string.song_details_pipeline), pipelineLine(track.pipeline))
}

@Composable
private fun EpisodeDetails(episode: SongDetailsUiState.Episode) {
    val context = LocalContext.current
    DetailsHeading(episode.title)
    DetailRow(stringResource(R.string.song_details_show), episode.show)
    DetailRow(stringResource(R.string.song_details_published), formatDate(episode.publishedAt.toEpochMilli()))
    DetailRow(stringResource(R.string.song_details_duration), formatDuration(episode.durationMs))
    DetailRow(stringResource(R.string.song_details_size), formatSize(context, episode.sizeBytes))
    DetailRow(stringResource(R.string.song_details_format), episode.format)
    DetailRow(stringResource(R.string.song_details_position), formatDuration(episode.playPositionMs))
}

@Composable
private fun DetailsHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(top = 12.dp, bottom = 16.dp)
            .semantics { heading() }
    )
}

/** One label and value, merged into a single "Label, value" TalkBack sentence. Omitted when value is null. */
@Composable
private fun DetailRow(label: String, value: String?) {
    if (value == null) return
    val merged = stringResource(R.string.song_details_row_a11y, label, value)
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = merged }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LABEL_WIDTH.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The format row with a lossless badge folded into both the value and its spoken label. */
@Composable
private fun FormatRow(format: String?, lossless: Boolean) {
    if (format == null) {
        return
    }
    val value = if (lossless) stringResource(R.string.song_details_format_lossless_a11y, format) else format
    DetailRow(stringResource(R.string.song_details_format), value)
}

@Composable
private fun numberOfTotal(number: Int?, total: Int?): String? = when {
    number == null -> null
    total != null -> stringResource(R.string.song_details_number_of_total, number, total)
    else -> stringResource(R.string.song_details_number, number)
}

/** The live pipeline line: sample rate, then bit depth or codec, then channels; null when empty. */
@Composable
private fun pipelineLine(pipeline: AudioPipelineFormat?): String? {
    if (pipeline == null || pipeline.isEmpty) return null
    val parts = buildList {
        pipeline.sampleRateHz?.let { add(stringResource(R.string.song_details_sample_rate, formatKilohertz(it))) }
        val middle = pipeline.bitDepth?.let { stringResource(R.string.song_details_bit_depth, it) } ?: pipeline.encoding?.let(::codecLabel)
        middle?.let(::add)
        pipeline.channelCount?.let { add(channelLabel(it)) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)
}

@Composable
private fun channelLabel(count: Int): String = when (count) {
    1 -> stringResource(R.string.song_details_channels_mono)
    2 -> stringResource(R.string.song_details_channels_stereo)
    else -> stringResource(R.string.song_details_channels_other, count)
}

/** A codec MIME like "audio/flac" shown as "FLAC". */
private fun codecLabel(mime: String): String = mime.substringAfterLast('/').uppercase()

private fun formatKilohertz(sampleRateHz: Int): String =
    NumberFormat.getInstance().apply { maximumFractionDigits = 1 }.format(sampleRateHz / HZ_PER_KHZ)

private fun formatInteger(value: Long): String = NumberFormat.getIntegerInstance().format(value)

private fun formatDuration(ms: Long): String = DateUtils.formatElapsedTime(ms / MS_PER_SECOND)

private fun formatSize(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

private fun formatRelative(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

private const val LABEL_WIDTH = 120
private const val MAX_RATING = 5
private const val MS_PER_SECOND = 1_000L
private const val HZ_PER_KHZ = 1_000.0
private const val SEPARATOR = ", "
