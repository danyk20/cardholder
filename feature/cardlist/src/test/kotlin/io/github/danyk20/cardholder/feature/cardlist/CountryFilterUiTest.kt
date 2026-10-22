package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.country.CountrySource
import io.github.danyk20.cardholder.core.domain.country.CountryState
import io.github.danyk20.cardholder.core.domain.country.CurrentCountry
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.testing.data.TestCards
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountryFilterUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val switzerland = Country(requireNotNull(CountryCode.of("CH")), "Switzerland")
    private val austria = Country(requireNotNull(CountryCode.of("AT")), "Austria")
    private val filterChanges = mutableListOf<Boolean>()
    private val picks = mutableListOf<CountryCode>()
    private var automaticCalls = 0
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
                        onFilterChange = { filterChanges.add(it) },
                        onPick = { picks.add(it) },
                        onAutomatic = { automaticCalls++ },
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
        selected: List<Card> = emptyList(),
    ) = CardListUiState.Success(
        cards = cards,
        query = "",
        visibleTypes = CardType.entries.toSet(),
        hasAnyCards = true,
        country = country,
        countries = listOf(switzerland, austria),
        hiddenElsewhere = hiddenElsewhere,
        selected = selected,
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
    fun `the country is no longer a row below the search`() {
        show(success(country = chip(detected = switzerland)))

        composeRule.onNodeWithText("Search cards").assertIsDisplayed()
        composeRule.onNodeWithText("🇨🇭  Switzerland").assertDoesNotExist()
    }

    @Test
    fun `the filter icon is hidden while no card is restricted to countries`() {
        show(success())

        composeRule.onNodeWithContentDescription(FILTER_DESCRIPTION).assertDoesNotExist()
    }

    @Test
    fun `the filter icon is shown once a card is restricted to countries`() {
        show(success(country = chip(detected = switzerland)))

        composeRule.onNodeWithContentDescription(FILTER_DESCRIPTION).assertIsDisplayed()
    }

    @Test
    fun `the filter icon sits to the left of the sort icon`() {
        show(success(country = chip(detected = switzerland)))

        val filter = composeRule.onNodeWithContentDescription(FILTER_DESCRIPTION).fetchSemanticsNode().boundsInRoot
        val sort = composeRule.onNodeWithContentDescription("Sort cards").fetchSemanticsNode().boundsInRoot
        assertTrue(filter.right <= sort.left)
    }

    @Test
    fun `the filter icon is hidden while cards are selected`() {
        show(success(country = chip(detected = switzerland), selected = listOf(TestCards.visa)))

        composeRule.onNodeWithContentDescription(FILTER_DESCRIPTION).assertDoesNotExist()
    }

    @Test
    fun `an active filter names the country and shows a dot`() {
        show(success(country = chip(detected = switzerland)))

        composeRule.onNodeWithContentDescription(FILTER_DESCRIPTION)
            .assert(hasStateDescription("Showing only cards valid in Switzerland"))
        composeRule.onNodeWithTag(COUNTRY_FILTER_BADGE_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a filter that is off says all cards are shown, without a dot`() {
        show(success(country = chip(detected = switzerland, filterEnabled = false)))

        composeRule.onNodeWithContentDescription(FILTER_DESCRIPTION).assert(hasStateDescription("Showing all cards"))
        composeRule.onNodeWithTag(COUNTRY_FILTER_BADGE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `without a known country the filter says all cards are shown, without a dot`() {
        show(success(country = chip(detected = null)))

        composeRule.onNodeWithContentDescription(FILTER_DESCRIPTION).assert(hasStateDescription("Showing all cards"))
        composeRule.onNodeWithTag(COUNTRY_FILTER_BADGE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the filter sheet offers the switch and the detected country first`() {
        show(success(country = chip(detected = switzerland)))

        openFilter()

        composeRule.onNodeWithText("Filter by country").assertIsDisplayed()
        composeRule.onNodeWithText("Only cards valid in 🇨🇭  Switzerland").assertIsDisplayed()
        composeRule.onNodeWithText("Automatic: 🇨🇭  Switzerland (mobile network)").assertIsDisplayed()
    }

    @Test
    fun `the switch turns an active filter off`() {
        show(success(country = chip(detected = switzerland)))
        openFilter()

        composeRule.onNodeWithText("Only cards valid in 🇨🇭  Switzerland").performClick()

        assertEquals(listOf(false), filterChanges)
    }

    @Test
    fun `the switch turns an inactive filter on`() {
        show(success(country = chip(detected = switzerland, filterEnabled = false)))
        openFilter()

        composeRule.onNodeWithText("Only cards valid in 🇨🇭  Switzerland").performClick()

        assertEquals(listOf(true), filterChanges)
    }

    @Test
    fun `without a known country the switch and the sheet say so`() {
        show(success(country = chip(detected = null)))

        openFilter()

        composeRule.onNodeWithText("Only cards valid in the current country").assertIsDisplayed()
        composeRule.onNodeWithText("Country unknown — all cards are shown").assertIsDisplayed()
        composeRule.onNodeWithText("Automatic: unknown").assertIsDisplayed()
    }

    @Test
    fun `picking a country calls back and closes the sheet`() {
        show(success(country = chip(detected = switzerland)))
        openFilter()

        composeRule.onNodeWithText("Austria").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(code("AT")), picks)
        composeRule.onNodeWithText("Filter by country").assertDoesNotExist()
    }

    @Test
    fun `automatic calls back and closes the sheet`() {
        show(success(country = chip(detected = switzerland, filterEnabled = false)))
        openFilter()

        composeRule.onNodeWithText("Automatic: 🇨🇭  Switzerland (mobile network)").performClick()
        composeRule.waitForIdle()

        assertEquals(1, automaticCalls)
        composeRule.onNodeWithText("Filter by country").assertDoesNotExist()
    }

    @Test
    fun `the search narrows the countries`() {
        show(success(country = chip(detected = switzerland)))
        openFilter()

        // The list behind the sheet has a search field of its own, so the sheet's is found by its hint.
        composeRule.onNode(hasSetTextAction() and hasAnyDescendant(hasText("Search countries")), useUnmergedTree = true)
            .performClick()
            .performTextInput("Aus")

        composeRule.onNodeWithText("Austria").assertIsDisplayed()
        composeRule.onNodeWithText("Switzerland").assertDoesNotExist()
    }

    private fun openFilter() {
        composeRule.onNodeWithContentDescription(FILTER_DESCRIPTION).performClick()
    }

    private fun chip(detected: Country?, filterEnabled: Boolean = true): CountryChip {
        val current = detected?.let { CurrentCountry(it.code, CountrySource.NETWORK) }
        return CountryChip(CountryState(effective = current, detected = current), filterEnabled = filterEnabled)
    }

    private fun code(value: String) = requireNotNull(CountryCode.of(value))
}

private const val FILTER_DESCRIPTION = "Filter by country"
