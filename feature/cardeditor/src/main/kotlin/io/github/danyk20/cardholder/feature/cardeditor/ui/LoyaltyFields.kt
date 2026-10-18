package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.Shop
import io.github.danyk20.cardholder.core.ui.barcodeFormatLabel
import io.github.danyk20.cardholder.feature.cardeditor.LoyaltyForm
import io.github.danyk20.cardholder.feature.cardeditor.R

internal class LoyaltyActions(
    val onShopSelected: (Shop) -> Unit,
    val onCustomShop: (String) -> Unit,
    val onCodeChange: (String) -> Unit,
    val onFormatChange: (BarcodeFormat) -> Unit,
    /** `null` when the camera scanner is unavailable. */
    val onScanBarcode: (() -> Unit)?,
)

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
