package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.feature.cardeditor.R
import io.github.danyk20.cardholder.feature.cardeditor.countryOptions
import java.text.Collator

/**
 * The countries a card is valid in, as a field that opens [CountryValidityDialog]. Shows nothing extra
 * for "all countries"; a single country gets its flag, and several countries are summarised.
 */
@Composable
internal fun ValidityField(validity: CardValidity, countries: List<Country>, onValidityChange: (CardValidity) -> Unit) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    PickerField(
        label = stringResource(R.string.editor_field_validity),
        value = validitySummary(validity, countries),
        onClick = { showDialog = true },
    )
    if (showDialog) {
        CountryValidityDialog(
            validity = validity,
            countries = countries,
            onDone = {
                onValidityChange(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun validitySummary(validity: CardValidity, countries: List<Country>): String = when (validity) {
    CardValidity.Everywhere -> stringResource(R.string.editor_validity_all)

    is CardValidity.Countries -> {
        val names = countryOptions(countries, validity.countries)
            .filter { it.code in validity.countries }
            .sortedWith(compareBy<Country, String>(Collator.getInstance()) { it.name })
        if (names.size == 1) {
            "${names.single().code.flagEmoji}  ${names.single().name}"
        } else {
            pluralStringResource(
                R.plurals.editor_validity_many,
                names.size,
                names.size,
                names.joinToString(", ") { it.name },
            )
        }
    }
}
