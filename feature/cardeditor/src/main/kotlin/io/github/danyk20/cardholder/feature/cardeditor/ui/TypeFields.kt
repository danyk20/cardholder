package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// Field building blocks shared by the bank, ID and loyalty forms.

/** Interaction source that invokes [onClick] when the (read-only) field is tapped. */
@Composable
private fun clickInteractionSource(onClick: () -> Unit): MutableInteractionSource {
    val source = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)
    LaunchedEffect(source) {
        source.interactions.collect { if (it is PressInteraction.Release) currentOnClick() }
    }
    return source
}

/** A read-only text field that opens a picker when clicked. */
@Composable
internal fun PickerField(label: String, value: String, onClick: () -> Unit, error: (@Composable () -> Unit)? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        isError = error != null,
        supportingText = error,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        interactionSource = clickInteractionSource(onClick),
    )
}
