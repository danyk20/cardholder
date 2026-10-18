package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.ui.CardSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoyaltyCardFaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(card: Card) = composeRule.setContent {
        CardholderTheme {
            LoyaltyCardFace(
                summary = CardSummary(card, card.title),
                code = LoyaltyCode(TestCards.loyaltyDetails.code, BarcodeFormat.EAN_13),
                onClick = {},
                onLongClick = {},
                modifier = Modifier.height(200.dp),
            )
        }
    }

    @Test
    fun `a favourite loyalty card says so, like the other cards`() {
        show(TestCards.loyalty.copy(isFavourite = true))

        composeRule.onAllNodes(hasContentDescription("Favourite", substring = true)).assertCountEquals(1)
    }

    @Test
    fun `other loyalty cards don't`() {
        show(TestCards.loyalty)

        composeRule.onAllNodes(hasContentDescription("Favourite", substring = true)).assertCountEquals(0)
    }
}
