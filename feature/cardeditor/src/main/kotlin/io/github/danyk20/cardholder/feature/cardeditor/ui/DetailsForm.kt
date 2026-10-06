package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.designsystem.theme.CardAccentColors
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardSide
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.ui.label
import io.github.danyk20.cardholder.core.ui.message
import io.github.danyk20.cardholder.feature.cardeditor.CardEditorUiState
import io.github.danyk20.cardholder.feature.cardeditor.R

/** Callbacks of the details form, grouped to keep composable signatures readable. */
internal class DetailsActions(
    val onTitleChange: (String) -> Unit,
    val onColorChange: (CardColor) -> Unit,
    val onLockedChange: (Boolean) -> Unit,
    val onOpenSecuritySettings: () -> Unit,
    val bank: BankActions,
    val id: IdActions,
    val loyalty: LoyaltyActions,
    val logo: LogoActions,
)

@Composable
internal fun DetailsForm(
    state: CardEditorUiState,
    sideActions: SideActions,
    actions: DetailsActions,
    contentPadding: PaddingValues,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .imePadding()
            .padding(16.dp)
            .widthIn(max = 600.dp),
    ) {
        when (state.type) {
            CardType.BANK -> {
                BankFields(state.bank, state.banks, state.errors, state.canProtect, actions.bank)
                LogoRow(state.logo, state.officialLogo != null, state.isDownloadingLogo, actions.logo)
            }

            CardType.ID -> IdFields(state.id, state.countries, state.errors, actions.id)

            CardType.LOYALTY -> {
                LoyaltyFields(state.loyalty, state.shops, state.errors, actions.loyalty)
                LogoRow(state.logo, state.officialLogo != null, state.isDownloadingLogo, actions.logo)
            }
        }
        OutlinedTextField(
            value = state.title,
            onValueChange = actions.onTitleChange,
            label = { Text(stringResource(R.string.editor_field_title)) },
            placeholder = { Text(state.defaultTitle) },
            supportingText = state.errors.supportingText(CardField.TITLE)
                ?: state.defaultTitle.takeIf { it.isNotEmpty() && state.title.isBlank() }?.let { default ->
                    { Text(stringResource(R.string.editor_field_title_hint, default)) }
                },
            isError = CardField.TITLE in state.errors,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.loyalty.shop !is BrandRef.Known || state.type != CardType.LOYALTY) {
            ColorPicker(selected = state.color, onSelect = actions.onColorChange)
        }
        Text(stringResource(R.string.editor_photos), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SideSlot(CardSide.FRONT, state.front, sideActions, Modifier.weight(1f), compact = true)
            SideSlot(CardSide.BACK, state.back, sideActions, Modifier.weight(1f), compact = true)
        }
        LockRow(
            isLocked = state.isLocked,
            canProtect = state.canProtect,
            onLockedChange = actions.onLockedChange,
            onOpenSecuritySettings = actions.onOpenSecuritySettings,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selected: CardColor, onSelect: (CardColor) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.editor_color), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardColor.entries.forEach { color ->
                val isSelected = color == selected
                val description = stringResource(color.label)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardAccentColors.getValue(color.name))
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape,
                        )
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(color) })
                        .semantics { contentDescription = description },
                ) {
                    if (isSelected) Icon(CardholderIcons.Check, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LockRow(
    isLocked: Boolean,
    canProtect: Boolean,
    onLockedChange: (Boolean) -> Unit,
    onOpenSecuritySettings: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = isLocked,
                enabled = canProtect,
                role = Role.Switch,
                onValueChange = onLockedChange,
            )
            .padding(vertical = 8.dp),
    ) {
        Icon(CardholderIcons.Lock, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.editor_lock), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    if (canProtect) R.string.editor_lock_description else R.string.editor_protection_unavailable,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!canProtect) {
                TextButton(onClick = onOpenSecuritySettings, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.editor_open_security_settings))
                }
            }
        }
        Switch(checked = isLocked, onCheckedChange = null, enabled = canProtect)
    }
}

/** Supporting text showing the validation error of [field], or `null` if there is none. */
internal fun Map<CardField, ValidationError>.supportingText(field: CardField): (@Composable () -> Unit)? =
    this[field]?.let { error -> { Text(stringResource(error.message)) } }
