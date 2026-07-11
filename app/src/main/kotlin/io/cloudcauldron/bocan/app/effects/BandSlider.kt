package io.cloudcauldron.bocan.app.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.playback.audio.EqBands
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One vertical EQ band: the gain in dB above, a vertical slider gated to the 0.5 dB grid,
 * and the band frequency below. TalkBack reads the band as its frequency (content) plus
 * its gain (state), e.g. "1 kilohertz, plus 3 decibels", satisfying the accessibility
 * requirement that slider values read as text.
 */
@Composable
fun BandSlider(centerHz: Double, gainDb: Double, onGain: (Double) -> Unit, modifier: Modifier = Modifier) {
    val frequencySpoken = frequencySpoken(centerHz)
    val gainSpoken = gainSpoken(gainDb)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(gainLabel(gainDb), style = MaterialTheme.typography.labelSmall)
        Box(modifier = Modifier.height(SLIDER_LENGTH.dp).width(SLIDER_BREADTH.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = gainDb.toFloat(),
                onValueChange = { onGain(EqBands.snap(it.toDouble())) },
                valueRange = EqBands.MIN_DB.toFloat()..EqBands.MAX_DB.toFloat(),
                steps = GRID_STEPS,
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = ROTATION_DEGREES
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            Constraints(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxWidth
                            )
                        )
                        layout(placeable.height, placeable.width) { placeable.place(-placeable.width, 0) }
                    }
                    .semantics {
                        contentDescription = frequencySpoken
                        stateDescription = gainSpoken
                    }
            )
        }
        Text(frequencyLabel(centerHz), style = MaterialTheme.typography.labelSmall)
    }
}

/** A signed one-decimal dB label, e.g. "+3.0" or "-2.5" (plain ASCII hyphen, never a dash). */
@Composable
private fun gainLabel(db: Double): String {
    val magnitude = "%.1f".format(abs(db))
    val signed = when {
        db > 0 -> "+$magnitude"
        db < 0 -> "-$magnitude"
        else -> magnitude
    }
    return stringResource(R.string.eq_decibels, signed)
}

@Composable
private fun frequencyLabel(centerHz: Double): String = if (centerHz >= HZ_PER_KHZ) {
    stringResource(R.string.eq_kilohertz, (centerHz / HZ_PER_KHZ).roundToInt())
} else {
    stringResource(R.string.eq_hertz, centerHz.roundToInt())
}

@Composable
private fun frequencySpoken(centerHz: Double): String = if (centerHz >= HZ_PER_KHZ) {
    stringResource(R.string.eq_kilohertz_spoken, (centerHz / HZ_PER_KHZ).roundToInt())
} else {
    stringResource(R.string.eq_hertz_spoken, centerHz.roundToInt())
}

@Composable
private fun gainSpoken(db: Double): String {
    val magnitude = "%.1f".format(abs(db))
    return when {
        db > 0 -> stringResource(R.string.eq_gain_spoken_positive, magnitude)
        db < 0 -> stringResource(R.string.eq_gain_spoken_negative, magnitude)
        else -> stringResource(R.string.eq_gain_spoken_zero)
    }
}

// The range -12..+12 dB on a 0.5 dB grid is 49 positions, so 47 steps sit between the ends.
private const val GRID_STEPS = 47
private const val ROTATION_DEGREES = 270f
private const val SLIDER_LENGTH = 160
private const val SLIDER_BREADTH = 40
private const val HZ_PER_KHZ = 1000.0
