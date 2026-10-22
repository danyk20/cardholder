package io.github.danyk20.cardholder.feature.carddetail.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.feature.carddetail.PlacesUiState
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlacesSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(count: Int, onOpen: () -> Unit = {}) = composeRule.setContent {
        PlacesSection(
            places = PlacesUiState(placeCount = count),
            cardType = CardType.LOYALTY,
            suggestByLocation = true,
            actions = PlaceActions(onOpenPlaces = onOpen),
        )
    }

    @Test
    fun `the places row shows how many places the card has and opens them`() {
        var opened = 0
        show(count = 3) { opened++ }

        composeRule.onNodeWithText("Places (3)").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `the places row shows even without places, so one can be added`() {
        var opened = 0
        show(count = 0) { opened++ }

        composeRule.onNodeWithText("Places (0)").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `forgetting places is no longer on the detail screen`() {
        show(count = 3)

        composeRule.onNodeWithText("Forget places").assertDoesNotExist()
    }
}
