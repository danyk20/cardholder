package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.domain.validation.BankCardValidator
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.Shop
import io.github.danyk20.cardholder.core.ui.barcodeFormatLabel
import io.github.danyk20.cardholder.feature.cardeditor.BankForm
import io.github.danyk20.cardholder.feature.cardeditor.IdForm
import io.github.danyk20.cardholder.feature.cardeditor.LoyaltyForm
import io.github.danyk20.cardholder.feature.cardeditor.R
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class BankActions(
    val onNumberChange: (String) -> Unit,
    val onExpiryChange: (String) -> Unit,
    val onHolderChange: (String) -> Unit,
    val onCvvChange: (String) -> Unit,
    val onRemoveStoredCvv: () -> Unit,
)

internal class IdActions(
    val onCountrySelected: (String) -> Unit,
    val onDocumentNumberChange: (String) -> Unit,
    val onExpiryChange: (LocalDate?) -> Unit,
)

internal class LoyaltyActions(
    val onShopSelected: (Shop) -> Unit,
    val onCustomShop: (String) -> Unit,
    val onCodeChange: (String) -> Unit,
    val onFormatChange: (BarcodeFormat) -> Unit,
    /** `null` when the camera scanner is unavailable. */
    val onScanBarcode: (() -> Unit)?,
)

@Composable
internal fun BankFields(
    form: BankForm,
    errors: Map<CardField, ValidationError>,
    canProtect: Boolean,
    actions: BankActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = form.number,
            onValueChange = actions.onNumberChange,
            label = { Text(stringResource(R.string.editor_field_number)) },
            visualTransformation = CardNumberTransformation(form.network),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = errors.supportingText(CardField.NUMBER)
                ?: form.number.takeIf { it.isNotEmpty() }?.let { { Text(form.network.displayName) } },
            isError = CardField.NUMBER in errors,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val expiry = BankCardValidator.parseExpiry(form.expiry)
            val expired = expiry != null && BankCardValidator.isExpired(expiry, YearMonth.now())
            OutlinedTextField(
                value = form.expiry,
                onValueChange = actions.onExpiryChange,
                label = { Text(stringResource(R.string.editor_field_expiry)) },
                visualTransformation = ExpiryTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = errors.supportingText(CardField.EXPIRY)
                    ?: if (expired) ({ Text(stringResource(R.string.editor_card_expired)) }) else null,
                isError = CardField.EXPIRY in errors,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.cvv,
                onValueChange = actions.onCvvChange,
                label = { Text(stringResource(R.string.editor_field_cvv)) },
                placeholder = if (form.hasStoredCvv && !form.removeStoredCvv) ({ Text("•••") }) else null,
                enabled = canProtect,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = CardField.CVV in errors,
                supportingText = errors.supportingText(CardField.CVV),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        CvvHint(form, canProtect, actions.onRemoveStoredCvv)
        OutlinedTextField(
            value = form.holder,
            onValueChange = actions.onHolderChange,
            label = { Text(stringResource(R.string.editor_field_holder)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            supportingText = errors.supportingText(CardField.HOLDER),
            isError = CardField.HOLDER in errors,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CvvHint(form: BankForm, canProtect: Boolean, onRemoveStoredCvv: () -> Unit) {
    when {
        !canProtect -> Text(stringResource(R.string.editor_protection_unavailable))

        form.hasStoredCvv && !form.removeStoredCvv && form.cvv.isEmpty() -> Row {
            Text(stringResource(R.string.editor_cvv_stored), Modifier.weight(1f).padding(top = 12.dp))
            TextButton(onClick = onRemoveStoredCvv) { Text(stringResource(R.string.editor_cvv_remove)) }
        }

        else -> Text(stringResource(R.string.editor_cvv_hint))
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoyaltyFields(
    form: LoyaltyForm,
    shops: List<Shop>,
    errors: Map<CardField, ValidationError>,
    actions: LoyaltyActions,
) {
    val resources = LocalResources.current
    var showShopPicker by rememberSaveable { mutableStateOf(false) }
    var formatExpanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PickerField(
            label = stringResource(R.string.editor_field_shop),
            value = form.shop?.name.orEmpty(),
            error = errors.supportingText(CardField.SHOP),
            onClick = { showShopPicker = true },
        )
        actions.onScanBarcode?.let { onScan ->
            FilledTonalButton(onClick = onScan) {
                Icon(CardholderIcons.ScanBarcode, contentDescription = null, Modifier.size(18.dp))
                Text(stringResource(R.string.editor_scan_barcode), Modifier.padding(start = 8.dp))
            }
        }
        OutlinedTextField(
            value = form.code,
            onValueChange = actions.onCodeChange,
            label = { Text(stringResource(R.string.editor_field_code)) },
            supportingText = errors.supportingText(CardField.CODE),
            isError = CardField.CODE in errors,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenuBox(expanded = formatExpanded, onExpandedChange = { formatExpanded = it }) {
            OutlinedTextField(
                value = barcodeFormatLabel(form.format),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.editor_field_format)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
                BarcodeFormat.entries.forEach { format ->
                    DropdownMenuItem(
                        text = { Text(barcodeFormatLabel(format)) },
                        onClick = {
                            actions.onFormatChange(format)
                            formatExpanded = false
                        },
                    )
                }
            }
        }
    }
    if (showShopPicker) {
        SearchablePickerDialog(
            title = stringResource(R.string.editor_choose_shop),
            searchHint = stringResource(R.string.editor_search_shop),
            items = shops,
            key = { it.id },
            label = { it.name },
            onSelect = {
                actions.onShopSelected(it)
                showShopPicker = false
            },
            onDismiss = { showShopPicker = false },
            customOptionLabel = { query -> resources.getString(R.string.editor_custom_shop, query) },
            onCustomOption = {
                actions.onCustomShop(it)
                showShopPicker = false
            },
        )
    }
}

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
private fun PickerField(label: String, value: String, onClick: () -> Unit, error: (@Composable () -> Unit)? = null) {
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
