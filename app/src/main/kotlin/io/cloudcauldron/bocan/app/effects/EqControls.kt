package io.cloudcauldron.bocan.app.effects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import kotlin.math.abs

/** A labelled horizontal slider whose current value is announced as its state description. */
@Composable
internal fun LabelledSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueText
            }
        )
    }
}

/** A section heading marked as a heading for TalkBack navigation. */
@Composable
internal fun SectionLabel(labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() }
    )
}

/** A signed one-decimal number; the sign placement is a translatable resource. */
@Composable
internal fun signed(value: Double): String {
    val magnitude = "%.1f".format(abs(value))
    return when {
        value > 0 -> stringResource(R.string.number_positive, magnitude)
        value < 0 -> stringResource(R.string.number_negative, magnitude)
        else -> magnitude
    }
}
