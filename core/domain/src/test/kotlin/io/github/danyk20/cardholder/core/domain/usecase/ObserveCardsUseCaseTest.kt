package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.model.CardSort
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ObserveCardsUseCaseTest {
    private val repository = FakeCardRepository()
    private val observeCards = ObserveCardsUseCase(repository)

    @Before
    fun setUp() {
        repository.add(TestCards.visa, TestCards.visaDetails, TestCards.VISA_CVV)
        repository.add(TestCards.idCard, TestCards.idCardDetails)
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)
    }

    @Test
    fun `returns all cards sorted by title without filters`() = runTest {
        assertEquals(
            listOf(TestCards.loyalty.id, TestCards.visa.id, TestCards.idCard.id),
            observeCards().first().map { it.id },
        )
    }

    @Test
    fun `sorts by date added, newest first`() = runTest {
        repository.add(TestCards.visa.copy(createdAt = Instant.parse("2026-03-01T00:00:00Z")), TestCards.visaDetails)
        repository.add(
            TestCards.loyalty.copy(createdAt = Instant.parse("2026-05-01T00:00:00Z")),
            TestCards.loyaltyDetails,
        )

        assertEquals(
            listOf(TestCards.loyalty.id, TestCards.visa.id, TestCards.idCard.id),
            observeCards(sort = CardSort.DATE_ADDED).first().map { it.id },
        )
    }

    @Test
    fun `custom order follows the stored positions`() = runTest {
        repository.reorder(listOf(TestCards.idCard.id, TestCards.loyalty.id, TestCards.visa.id))

        assertEquals(
            listOf(TestCards.idCard.id, TestCards.loyalty.id, TestCards.visa.id),
            observeCards(sort = CardSort.CUSTOM).first().map { it.id },
        )
    }

    @Test
    fun `shows only the selected types`() = runTest {
        assertEquals(listOf(TestCards.idCard.id), observeCards(types = setOf(CardType.ID)).first().map { it.id })
        assertEquals(
            listOf(TestCards.loyalty.id, TestCards.visa.id),
            observeCards(types = setOf(CardType.BANK, CardType.LOYALTY)).first().map { it.id },
        )
        assertEquals(emptyList(), observeCards(types = emptySet()).first())
    }

    @Test
    fun `searches title, shop and network case-insensitively`() = runTest {
        assertEquals(listOf(TestCards.loyalty.id), observeCards(query = "corner").first().map { it.id })
        assertEquals(listOf(TestCards.visa.id), observeCards(query = "VISA").first().map { it.id })
        assertEquals(listOf(TestCards.idCard.id), observeCards(query = "swiss").first().map { it.id })
        assertEquals(emptyList(), observeCards(query = "nothing").first())
    }
}
