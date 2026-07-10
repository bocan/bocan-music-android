package io.cloudcauldron.bocan.app.pairing

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R

private const val CODE_LENGTH = 6

/**
 * A six digit code entry: one hidden text field with a numeric keypad, rendered
 * as six boxes. Calls [onCodeComplete] when all six digits are present. When
 * [isError] flips true (a wrong code), the field clears for a fresh attempt.
 */
@Composable
fun CodeEntryField(onCodeComplete: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, isError: Boolean = false) {
    var value by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isError) {
        if (isError) {
            value = ""
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BasicTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(CODE_LENGTH)
            value = digits
            if (digits.length == CODE_LENGTH) {
                focusManager.clearFocus()
                onCodeComplete(digits)
            }
        },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        modifier = modifier.focusRequester(focusRequester),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(CODE_LENGTH) { index ->
                    CodeDigitBox(
                        digit = value.getOrNull(index)?.toString().orEmpty(),
                        highlighted = index == value.length,
                        isError = isError,
                        position = index + 1
                    )
                }
            }
        }
    )
}

@Composable
private fun CodeDigitBox(digit: String, highlighted: Boolean, isError: Boolean, position: Int) {
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val description = stringResource(R.string.pairing_code_digit, position)
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 56.dp)
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
