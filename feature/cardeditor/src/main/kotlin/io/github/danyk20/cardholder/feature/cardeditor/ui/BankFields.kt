package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import io.github.danyk20.cardholder.core.domain.validation.BankCardValidator
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.InputStatus
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.Bank
import io.github.danyk20.cardholder.core.ui.R as UiR
import io.github.danyk20.cardholder.core.ui.SearchablePickerDialog
import io.github.danyk20.cardholder.feature.cardeditor.BankForm
import io.github.danyk20.cardholder.feature.cardeditor.R
import java.time.YearMonth

internal class BankActions(
    val onBankSelected: (Bank) -> Unit,
    val onCustomBank: (String) -> Unit,
    /** `null` when the device has no NFC. */
    val onReadWithNfc: (() -> Unit)?,
    val onNumberChange: (String) -> Unit,
    val onExpiryChange: (String) -> Unit,
    val onHolderChange: (String) -> Unit,
    val onCvvChange: (String) -> Unit,
    val onRemoveStoredCvv: () -> Unit,
)

@Composable
internal fun BankFields(
    form: BankForm,
    banks: List<Bank>,
    errors: Map<CardField, ValidationError>,
    canProtect: Boolean,
    actions: BankActions,
) {
    var showBankPicker by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PickerField(
            label = stringResource(R.string.editor_field_bank),
            value = form.issuer?.name.orEmpty(),
            onClick = { showBankPicker = true },
        )
        actions.onReadWithNfc?.let { onRead ->
            FilledTonalButton(onClick = onRead) {
                Icon(CardholderIcons.Nfc, contentDescription = null, Modifier.size(18.dp))
                Text(stringResource(R.string.editor_read_nfc), Modifier.padding(start = 8.dp))
            }
        }
        // Checked while typing, so a typo shows up before saving.
        val numberStatus = BankCardValidator.numberStatus(form.number)
        OutlinedTextField(
            value = form.number,
            onValueChange = actions.onNumberChange,
            label = { Text(stringResource(R.string.editor_field_number)) },
            visualTransformation = CardNumberTransformation(form.network),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = errors.supportingText(CardField.NUMBER)
                ?: if (numberStatus == InputStatus.INVALID) {
                    { Text(stringResource(UiR.string.error_invalid_checksum)) }
                } else {
                    form.number.takeIf { it.isNotEmpty() }?.let { { Text(form.network.displayName) } }
                },
            trailingIcon = validIcon(numberStatus, R.string.editor_number_valid),
            isError = CardField.NUMBER in errors || numberStatus == InputStatus.INVALID,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val expiry = BankCardValidator.parseExpiry(form.expiry)
            val expired = expiry != null && BankCardValidator.isExpired(expiry, YearMonth.now())
            val expiryStatus = BankCardValidator.expiryStatus(form.expiry)
            OutlinedTextField(
                value = form.expiry,
                onValueChange = actions.onExpiryChange,
                label = { Text(stringResource(R.string.editor_field_expiry)) },
                visualTransformation = ExpiryTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = errors.supportingText(CardField.EXPIRY)
                    ?: when {
                        expiryStatus == InputStatus.INVALID -> ({ Text(stringResource(UiR.string.error_invalid_date)) })
                        expired -> ({ Text(stringResource(R.string.editor_card_expired)) })
                        else -> null
                    },
                trailingIcon = validIcon(
                    if (expired) InputStatus.INCOMPLETE else expiryStatus,
                    R.string.editor_expiry_valid,
                ),
                isError = CardField.EXPIRY in errors || expiryStatus == InputStatus.INVALID,
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
    if (showBankPicker) {
        BankPickerDialog(
            banks = banks,
            onSelect = {
                actions.onBankSelected(it)
                showBankPicker = false
            },
            onCustom = {
                actions.onCustomBank(it)
                showBankPicker = false
            },
            onDismiss = { showBankPicker = false },
        )
    }
}

@Composable
private fun BankPickerDialog(
    banks: List<Bank>,
    onSelect: (Bank) -> Unit,
    onCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    SearchablePickerDialog(
        title = stringResource(R.string.editor_choose_bank),
        searchHint = stringResource(R.string.editor_search_bank),
        items = banks,
        key = { it.id },
        label = { it.name },
        leading = { bank -> Text(bank.countries.singleOrNull()?.flagEmoji ?: "\uD83C\uDF10") },
        onSelect = onSelect,
        onDismiss = onDismiss,
        customOptionLabel = { query -> resources.getString(R.string.editor_custom_shop, query) },
        onCustomOption = onCustom,
    )
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

/** A check mark once a field was verified, e.g. a card number with a valid check digit. */
private fun validIcon(status: InputStatus, description: Int): (@Composable () -> Unit)? =
    if (status == InputStatus.VALID) {
        { Icon(CardholderIcons.Check, stringResource(description), tint = MaterialTheme.colorScheme.primary) }
    } else {
        null
    }
