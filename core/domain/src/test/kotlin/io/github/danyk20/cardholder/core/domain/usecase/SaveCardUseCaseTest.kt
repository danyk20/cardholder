package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.validation.CardDraftValidator
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.ShopRef
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SaveCardUseCaseTest {
    private val repository = FakeCardRepository()
    private val saveCard = SaveCardUseCase(repository, CardDraftValidator())

    private val bankDraft = CardDraft(
        title = "  My card ",
        color = CardColor.BLUE,
        content = CardContent.Bank(
            details = CardDetails.Bank("4111 1111 1111 1111", YearMonth.of(2030, 1), " Jane Doe "),
            cvv = CvvChange.Set("123"),
        ),
    )

    @Test
    fun `normalizes and saves a valid bank card`() = runTest {
        val result = saveCard(bankDraft)

        assertIs<SaveCardResult.Saved>(result)
        val saved = repository.savedDrafts.single()
        assertEquals("My card", saved.title)
        val content = saved.content as CardContent.Bank
        assertEquals("4111111111111111", content.details.number)
        assertEquals("Jane Doe", content.details.holder)
    }

    @Test
    fun `reports every invalid field and does not save`() = runTest {
        val draft = bankDraft.copy(
            title = "",
            content = CardContent.Bank(
                details = CardDetails.Bank("4111111111111112", YearMonth.of(2030, 1), ""),
                cvv = CvvChange.Set("12"),
            ),
        )

        val result = saveCard(draft)

        assertEquals(
            SaveCardResult.Invalid(
                mapOf(
                    CardField.TITLE to ValidationError.REQUIRED,
                    CardField.NUMBER to ValidationError.INVALID_CHECKSUM,
                    CardField.HOLDER to ValidationError.REQUIRED,
                    CardField.CVV to ValidationError.INVALID_LENGTH,
                ),
            ),
            result,
        )
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun `uppercases code 39 loyalty codes`() = runTest {
        val draft = CardDraft(
            title = "Gym",
            color = CardColor.GREEN,
            content = CardContent.Loyalty(
                shop = ShopRef.Custom("Gym"),
                format = BarcodeFormat.CODE_39,
                details = CardDetails.Loyalty(" abc123 "),
            ),
        )

        assertIs<SaveCardResult.Saved>(saveCard(draft))
        assertEquals("ABC123", (repository.savedDrafts.single().content as CardContent.Loyalty).details.code)
    }

    @Test
    fun `updating a locked card requires authentication`() = runTest {
        repository.add(TestCards.idCard, TestCards.idCardDetails)
        val draft = CardDraft(
            id = TestCards.idCard.id,
            title = "ID",
            color = CardColor.RED,
            content = CardContent.Id(
                country = (TestCards.idCard.info as CardInfo.Id).country,
                details = TestCards.idCardDetails,
            ),
            isLocked = true,
        )

        assertEquals(SaveCardResult.AuthenticationRequired, saveCard(draft))
        repository.isAuthenticated = true
        assertIs<SaveCardResult.Saved>(saveCard(draft))
    }
}
