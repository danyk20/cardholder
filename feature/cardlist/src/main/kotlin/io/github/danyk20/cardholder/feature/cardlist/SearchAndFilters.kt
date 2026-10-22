package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.ui.icon
import io.github.danyk20.cardholder.core.ui.label

/** The search field, one filter chip per card type and, when cards are restricted, the country row above the list. */
@Composable
internal fun SearchAndFilters(
    query: String,
    visibleTypes: Set<CardType>,
    onQueryChange: (String) -> Unit,
    onTypeToggled: (CardType) -> Unit,
    country: CountryChip?,
    countries: List<Country>,
    countryActions: CountryActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.cardlist_search)) },
            leadingIcon = { Icon(CardholderIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(CardholderIcons.Close, contentDescription = stringResource(R.string.cardlist_clear_search))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            CardType.entries.forEach { type ->
                FilterChip(
                    selected = type in visibleTypes,
                    onClick = { onTypeToggled(type) },
                    label = { Text(stringResource(type.label)) },
                    leadingIcon = {
                        Icon(
                            if (type in visibleTypes) CardholderIcons.Check else type.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
        country?.let { CountryChipRow(it, countries, countryActions) }
    }
}
