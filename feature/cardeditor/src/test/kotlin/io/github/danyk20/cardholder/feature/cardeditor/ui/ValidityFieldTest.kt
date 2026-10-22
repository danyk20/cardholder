package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValidityFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val austria = Country(code("AT"), "Austria")
    private val switzerland = Country(code("CH"), "Switzerland")
    private val countries = listOf(switzerland, austria)

    @Test
    fun `everywhere shows all countries`() {
        assertFieldShows(CardValidity.Everywhere, "All countries")
    }

    @Test
    fun `one country shows its flag and name`() {
        assertFieldShows(CardValidity.Countries(setOf(switzerland.code)), "🇨🇭  Switzerland")
    }

    @Test
    fun `several countries show a globe and the sorted names`() {
        assertFieldShows(
            CardValidity.Countries(setOf(switzerland.code, austria.code)),
            "🌐 2 countries: Austria, Switzerland",
        )
    }

    @Test
    fun `a code the catalogue lacks is shown as its code`() {
        assertFieldShows(CardValidity.Countries(setOf(code("XK"))), "🇽🇰  XK")
    }

    private fun assertFieldShows(validity: CardValidity, text: String) {
        composeRule.setContent {
            CardholderTheme { ValidityField(validity, countries, onValidityChange = {}) }
        }

        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun code(value: String) = requireNotNull(CountryCode.of(value))
}
