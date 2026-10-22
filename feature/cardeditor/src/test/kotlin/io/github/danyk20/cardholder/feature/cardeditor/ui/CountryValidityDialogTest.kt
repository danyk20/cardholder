package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.Region
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountryValidityDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val austria = Country(code("AT"), "Austria")
    private val france = Country(code("FR"), "France")
    private val germany = Country(code("DE"), "Germany")
    private val switzerland = Country(code("CH"), "Switzerland")
    private val countries = listOf(austria, france, germany, switzerland)

    /** The validity the dialog reported with Done, or `null` while it is open. */
    private var result: CardValidity? = null

    private fun showDialog(validity: CardValidity) {
        composeRule.setContent {
            CardholderTheme {
                CountryValidityDialog(
                    validity = validity,
                    countries = countries,
                    onDone = { result = it },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `done with nothing selected means all countries`() {
        showDialog(CardValidity.Countries(setOf(switzerland.code)))

        composeRule.onNodeWithText("Switzerland").performClick()
        composeRule.onNodeWithText("Done").performClick()

        assertEquals(CardValidity.Everywhere, result)
    }

    @Test
    fun `choosing countries makes the card valid only in them`() {
        showDialog(CardValidity.Everywhere)

        composeRule.onNodeWithText("Selected countries").performClick()
        composeRule.onNodeWithText("Austria").performClick()
        composeRule.onNodeWithText("Done").performClick()

        assertEquals(CardValidity.Countries(setOf(austria.code)), result)
    }

    @Test
    fun `the all countries choice ignores the selection`() {
        showDialog(CardValidity.Countries(setOf(switzerland.code)))

        composeRule.onNodeWithText("All countries").performClick()
        composeRule.onNodeWithText("Done").performClick()

        assertEquals(CardValidity.Everywhere, result)
    }

    @Test
    fun `a region chip selects all its countries`() {
        showDialog(CardValidity.Everywhere)

        composeRule.onNodeWithText("Selected countries").performClick()
        composeRule.onNodeWithText("EU").performClick()
        composeRule.onNodeWithText("Done").performClick()

        assertEquals(CardValidity.Countries(Region.EU.countries), result)
    }

    @Test
    fun `tapping a selected region chip clears it`() {
        showDialog(CardValidity.Countries(Region.EEA.countries))

        composeRule.onNodeWithText("EEA").performClick()
        composeRule.onNodeWithText("Done").performClick()

        assertEquals(CardValidity.Everywhere, result)
    }

    @Test
    fun `search matches the country code as well as the name`() {
        showDialog(CardValidity.Everywhere)
        composeRule.onNodeWithText("Selected countries").performClick()

        composeRule.onNode(hasSetTextAction()).performTextInput("fr")

        composeRule.onNodeWithText("France").assertIsDisplayed()
        composeRule.onAllNodesWithText("Germany").assertCountEquals(0)
    }

    @Test
    fun `a selected code the catalogue lacks can be unticked`() {
        showDialog(CardValidity.Countries(setOf(code("XK"))))

        composeRule.onNodeWithText("XK").performClick()
        composeRule.onNodeWithText("Done").performClick()

        assertEquals(CardValidity.Everywhere, result)
    }

    private fun code(value: String) = requireNotNull(CountryCode.of(value))
}
