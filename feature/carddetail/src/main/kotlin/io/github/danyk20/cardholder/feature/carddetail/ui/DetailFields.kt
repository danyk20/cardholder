package io.github.danyk20.cardholder.feature.carddetail.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.ui.barcodeFormatLabel
import io.github.danyk20.cardholder.core.ui.formatCardNumber
import io.github.danyk20.cardholder.core.ui.formatExpiry
import io.github.danyk20.cardholder.feature.carddetail.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Copy / reveal callbacks of the detail rows. */
internal class FieldActions(
    val onCopy: (label: String, value: String) -> Unit,
    val onRevealCvv: () -> Unit,
    val onHideCvv: () -> Unit,
)

@Composable
internal fun DetailFields(card: Card, subtitle: String, details: CardDetails, cvv: String?, actions: FieldActions) {
    Column {
        when (details) {
            is CardDetails.Bank -> {
                val network = (card.info as? CardInfo.Bank)?.network
                (card.info as? CardInfo.Bank)?.issuer?.let { Field(R.string.field_bank, it.name, monospace = false) }
                CopyableField(
                    R.string.field_number,
                    formatCardNumber(details.number),
                    actions,
                    copyValue = details.number,
                )
                CopyableField(R.string.field_expiry, formatExpiry(details.expiry), actions)
                CopyableField(R.string.field_holder, details.holder, actions)
                if (card.hasCvv) CvvField(cvv, actions)
                network?.let { Field(R.string.field_network, it.displayName, monospace = false) }
            }

            is CardDetails.Id -> {
                Field(R.string.field_country, subtitle, monospace = false)
                details.documentNumber?.let { CopyableField(R.string.field_document_number, it, actions) }
                details.expiry?.let {
                    Field(
                        R.string.field_expiry,
                        it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        monospace = false,
                    )
                }
            }

            is CardDetails.Loyalty -> {
                val info = card.info as? CardInfo.Loyalty
                info?.let { Field(R.string.field_shop, it.shop.name, monospace = false) }
                CopyableField(R.string.field_code, details.code, actions)
                info?.let { Field(R.string.field_format, barcodeFormatLabel(it.format), monospace = false) }
            }
        }
    }
}

@Composable
private fun Field(label: Int, value: String, monospace: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    ListItem(
        overlineContent = { Text(stringResource(label)) },
        headlineContent = { Text(value, fontFamily = if (monospace) FontFamily.Monospace else null) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(),
    )
}

@Composable
private fun CopyableField(label: Int, value: String, actions: FieldActions, copyValue: String = value) {
    val labelText = stringResource(label)
    Field(label, value, trailing = {
        IconButton(onClick = { actions.onCopy(labelText, copyValue) }) {
            Icon(CardholderIcons.Copy, contentDescription = stringResource(R.string.detail_copy, labelText))
        }
    })
}

@Composable
private fun CvvField(cvv: String?, actions: FieldActions) {
    if (cvv == null) {
        Field(R.string.field_cvv, "•••", trailing = {
            TextButton(onClick = actions.onRevealCvv) { Text(stringResource(R.string.detail_show_cvv)) }
        })
    } else {
        val label = stringResource(R.string.field_cvv)
        Field(R.string.field_cvv, cvv, trailing = {
            Row {
                IconButton(onClick = { actions.onCopy(label, cvv) }) {
                    Icon(CardholderIcons.Copy, contentDescription = stringResource(R.string.detail_copy, label))
                }
                IconButton(onClick = actions.onHideCvv) {
                    Icon(CardholderIcons.Hide, contentDescription = stringResource(R.string.detail_hide_cvv))
                }
            }
        })
    }
}
