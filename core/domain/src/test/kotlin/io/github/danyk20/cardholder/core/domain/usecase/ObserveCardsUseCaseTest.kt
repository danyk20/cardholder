package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.model.CardSort
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
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
            observeCards().first().cards.map { it.id },
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
            observeCards(sort = CardSort.DATE_ADDED).first().cards.map { it.id },
        )
    }

    @Test
    fun `custom order follows the stored positions`() = runTest {
        repository.reorder(listOf(TestCards.idCard.id, TestCards.loyalty.id, TestCards.visa.id))

        assertEquals(
            listOf(TestCards.idCard.id, TestCards.loyalty.id, TestCards.visa.id),
            observeCards(sort = CardSort.CUSTOM).first().cards.map { it.id },
        )
    }

    @Test
    fun `shows only the selected types`() = runTest {
        assertEquals(listOf(TestCards.idCard.id), observeCards(types = setOf(CardType.ID)).first().cards.map { it.id })
        assertEquals(
            listOf(TestCards.loyalty.id, TestCards.visa.id),
            observeCards(types = setOf(CardType.BANK, CardType.LOYALTY)).first().cards.map { it.id },
        )
        assertEquals(emptyList(), observeCards(types = emptySet()).first().cards)
    }

    @Test
    fun `searches title, shop and network case-insensitively`() = runTest {
        assertEquals(listOf(TestCards.loyalty.id), observeCards(query = "corner").first().cards.map { it.id })
        assertEquals(listOf(TestCards.visa.id), observeCards(query = "VISA").first().cards.map { it.id })
        assertEquals(listOf(TestCards.idCard.id), observeCards(query = "swiss").first().cards.map { it.id })
        assertEquals(emptyList(), observeCards(query = "nothing").first().cards)
    }

    @Test
    fun `a country hides the cards valid only elsewhere and counts them`() = runTest {
        repository.add(TestCards.loyalty.copy(validity = restrictedTo("CH")), TestCards.loyaltyDetails)
        repository.add(TestCards.idCard.copy(validity = restrictedTo("DE")), TestCards.idCardDetails)

        val result = observeCards(country = code("CH")).first()

        assertEquals(listOf(TestCards.loyalty.id, TestCards.visa.id), result.cards.map { it.id })
        assertEquals(1, result.hiddenElsewhere)
    }

    @Test
    fun `the count only covers the chosen types`() = runTest {
        repository.add(TestCards.idCard.copy(validity = restrictedTo("DE")), TestCards.idCardDetails)

        val idsOnly = observeCards(types = setOf(CardType.ID), country = code("CH")).first()
        val bankOnly = observeCards(types = setOf(CardType.BANK), country = code("CH")).first()

        assertEquals(emptyList(), idsOnly.cards)
        assertEquals(1, idsOnly.hiddenElsewhere)
        assertEquals(listOf(TestCards.visa.id), bankOnly.cards.map { it.id })
        assertEquals(0, bankOnly.hiddenElsewhere)
    }

    @Test
    fun `an unknown country hides nothing`() = runTest {
        repository.add(TestCards.loyalty.copy(validity = restrictedTo("CH")), TestCards.loyaltyDetails)

        val result = observeCards(country = null).first()

        assertEquals(3, result.cards.size)
        assertEquals(0, result.hiddenElsewhere)
    }

    @Test
    fun `a search looks through all cards whatever the country`() = runTest {
        repository.add(TestCards.idCard.copy(validity = restrictedTo("DE")), TestCards.idCardDetails)

        val result = observeCards(query = "swiss", country = code("CH")).first()

        assertEquals(listOf(TestCards.idCard.id), result.cards.map { it.id })
        assertEquals(0, result.hiddenElsewhere)
    }

    @Test
    fun `a card valid in the country is shown even when restricted to several`() = runTest {
        repository.add(
            TestCards.visa.copy(validity = CardValidity.of(listOf(code("AT"), code("CH")))),
            TestCards.visaDetails,
        )

        val result = observeCards(types = setOf(CardType.BANK), country = code("CH")).first()

        assertEquals(listOf(TestCards.visa.id), result.cards.map { it.id })
    }

    private fun code(value: String) = requireNotNull(CountryCode.of(value))

    private fun restrictedTo(vararg values: String) = CardValidity.of(values.map(::code))
}
