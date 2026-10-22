package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.ui.SearchablePickerDialog
import io.github.danyk20.cardholder.core.ui.secureDialogProperties
import io.github.danyk20.cardholder.feature.cardeditor.IdForm
import io.github.danyk20.cardholder.feature.cardeditor.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class IdActions(
    val onCountrySelected: (String) -> Unit,
    val onDocumentNumberChange: (String) -> Unit,
    val onExpiryChange: (LocalDate?) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdFields(
    form: IdForm,
    countries: List<Country>,
    errors: Map<CardField, ValidationError>,
    actions: IdActions,
) {
    var showCountryPicker by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PickerField(
            label = stringResource(R.string.editor_field_country),
            value = form.country?.let { "${it.code.flagEmoji}  ${it.name}" }.orEmpty(),
            error = errors.supportingText(CardField.COUNTRY),
            onClick = { showCountryPicker = true },
        )
        OutlinedTextField(
            value = form.documentNumber,
            onValueChange = actions.onDocumentNumberChange,
            label = { Text(stringResource(R.string.editor_field_document_number)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PickerField(
            label = stringResource(R.string.editor_field_id_expiry),
            value = form.expiry?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)).orEmpty(),
            onClick = { showDatePicker = true },
        )
    }
    if (showCountryPicker) {
        SearchablePickerDialog(
            title = stringResource(R.string.editor_choose_country),
            searchHint = stringResource(R.string.editor_search_country),
            items = countries,
            key = { it.code.value },
            label = { it.name },
            leading = { Text(it.code.flagEmoji) },
            onSelect = {
                actions.onCountrySelected(it.code.value)
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false },
        )
    }
    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = form.expiry?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            properties = secureDialogProperties(),
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onExpiryChange(
                            state.selectedDateMillis?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            },
                        )
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.editor_ok)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        actions.onExpiryChange(null)
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.editor_clear_date)) }
            },
        ) { DatePicker(state = state) }
    }
}
