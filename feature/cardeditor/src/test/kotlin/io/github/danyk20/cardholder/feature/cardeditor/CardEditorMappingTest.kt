package io.github.danyk20.cardholder.feature.cardeditor

import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.testing.data.TestCards
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class CardEditorMappingTest {
    @Test
    fun `expiry is shown as MMYY`() {
        assertEquals("0328", YearMonth.of(2028, 3).toExpiryDigits())
        assertEquals("1200", YearMonth.of(2100, 12).toExpiryDigits())
    }

    @Test
    fun `a loaded card turns back into the same content`() {
        val state = CardEditorUiState().populatedFrom(TestCards.loyalty, TestCards.loyaltyDetails) { null }

        val (draft, errors) = state.toDraft(original = TestCards.loyalty)

        assertTrue(errors.isEmpty())
        assertEquals(TestCards.loyalty.title, draft.title)
        val content = assertIs<CardContent.Loyalty>(draft.content)
        assertEquals(TestCards.loyaltyDetails, content.details)
        assertEquals(ImageChange.Keep, draft.logo)
    }

    @Test
    fun `values the form can't represent are reported`() {
        val (_, idErrors) = CardEditorUiState(type = CardType.ID).toDraft(original = null)
        val (_, bankErrors) = CardEditorUiState(type = CardType.BANK, bank = BankForm(expiry = "13")).toDraft(null)

        assertEquals(ValidationError.REQUIRED, idErrors[CardField.COUNTRY])
        assertTrue(CardField.EXPIRY in bankErrors)
    }
}
