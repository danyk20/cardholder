package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.country.CountrySource
import io.github.danyk20.cardholder.core.domain.country.CountryState
import io.github.danyk20.cardholder.core.domain.country.CurrentCountry
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.CountryCode
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountryFilterUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val switzerland = Country(requireNotNull(CountryCode.of("CH")), "Switzerland")
    private val austria = Country(requireNotNull(CountryCode.of("AT")), "Austria")
    private var showAllCalls = 0

    private fun show(state: CardListUiState.Success) {
        composeRule.setContent {
            CardholderTheme {
                CardListScreen(
                    uiState = state,
                    sortActions = SortActions({}, {}, { _, _ -> }, {}, {}, {}, {}),
                    onQueryChange = {},
                    onTypeToggled = {},
                    onCardClick = {},
                    onCardLongClick = {},
                    onAddCard = {},
                    onOpenSettings = {},
                    countryActions = CountryActions(
                        onFilterChange = {},
                        onPick = {},
                        onAutomatic = {},
                        onShowAll = { showAllCalls++ },
                    ),
                )
            }
        }
    }

    private fun success(
        cards: List<CardListItem> = emptyList(),
        country: CountryChip? = null,
        hiddenElsewhere: Int = 0,
    ) = CardListUiState.Success(
        cards = cards,
        query = "",
        visibleTypes = CardType.entries.toSet(),
        hasAnyCards = true,
        country = country,
        countries = listOf(switzerland, austria),
        hiddenElsewhere = hiddenElsewhere,
    )

    @Test
    fun `the footer counts the hidden cards and show all shows them`() {
        show(success(hiddenElsewhere = 3, country = chip(detected = switzerland)))

        composeRule.onNodeWithText("3 cards for other countries are hidden").assertIsDisplayed()
        composeRule.onNodeWithText("Show all").performClick()

        assertEquals(1, showAllCalls)
    }

    @Test
    fun `a single hidden card is counted in the singular`() {
        show(success(hiddenElsewhere = 1, country = chip(detected = switzerland)))

        composeRule.onNodeWithText("1 card for other countries is hidden").assertIsDisplayed()
    }

    @Test
    fun `when every card is hidden the footer replaces the empty message`() {
        show(success(hiddenElsewhere = 2, country = chip(detected = switzerland)))

        composeRule.onNodeWithText("2 cards for other countries are hidden").assertIsDisplayed()
        composeRule.onAllNodesWithText("No cards match your search").assertCountEquals(0)
    }

    @Test
    fun `the country row names the country in use`() {
        show(success(country = chip(detected = switzerland)))

        composeRule.onNodeWithText("🇨🇭  Switzerland").assertIsDisplayed()
    }

    @Test
    fun `without a known country the row says so`() {
        show(success(country = chip(detected = null)))

        composeRule.onNodeWithText("Country unknown").assertIsDisplayed()
    }

    @Test
    fun `the change country button offers the detected country first`() {
        show(success(country = chip(detected = switzerland)))

        composeRule.onNodeWithContentDescription("Change country").performClick()

        composeRule.onNodeWithText("Automatic: 🇨🇭  Switzerland (mobile network)")
            .assertIsDisplayed()
    }

    private fun chip(detected: Country?): CountryChip {
        val current = detected?.let { CurrentCountry(it.code, CountrySource.NETWORK) }
        return CountryChip(CountryState(effective = current, detected = current), filterEnabled = true)
    }
}
