package io.github.danyk20.cardholder.feature.carddetail.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.ui.flagAndName
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailFieldsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val austria = Country(requireNotNull(CountryCode.of("AT")), "Austria")
    private val switzerland = Country(requireNotNull(CountryCode.of("CH")), "Switzerland")

    private fun show(validCountries: List<Country>) {
        composeRule.setContent {
            CardholderTheme {
                DetailFields(
                    card = TestCards.loyalty,
                    subtitle = TestCards.loyalty.title,
                    details = TestCards.loyaltyDetails,
                    cvv = null,
                    validCountries = validCountries,
                    actions = FieldActions(onCopy = { _, _ -> }, onRevealCvv = {}, onHideCvv = {}),
                )
            }
        }
    }

    @Test
    fun `a restricted card lists every country it is valid in`() {
        show(listOf(austria, switzerland))

        composeRule.onNodeWithText("Valid in").assertIsDisplayed()
        composeRule.onNodeWithText(austria.flagAndName() + "\n" + switzerland.flagAndName()).assertIsDisplayed()
    }

    @Test
    fun `a card valid everywhere has no valid in row`() {
        show(emptyList())

        composeRule.onAllNodesWithText("Valid in").assertCountEquals(0)
    }
}
