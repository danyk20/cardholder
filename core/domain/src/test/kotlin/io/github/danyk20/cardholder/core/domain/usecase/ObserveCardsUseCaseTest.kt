package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
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
    fun `filters by type`() = runTest {
        assertEquals(listOf(TestCards.idCard.id), observeCards(type = CardType.ID).first().map { it.id })
    }

    @Test
    fun `searches title, shop and network case-insensitively`() = runTest {
        assertEquals(listOf(TestCards.loyalty.id), observeCards(query = "corner").first().map { it.id })
        assertEquals(listOf(TestCards.visa.id), observeCards(query = "VISA").first().map { it.id })
        assertEquals(listOf(TestCards.idCard.id), observeCards(query = "swiss").first().map { it.id })
        assertEquals(emptyList(), observeCards(query = "nothing").first())
    }
}
