package io.github.danyk20.cardholder.quickaccess

import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.testing.data.TestCards
import kotlin.test.assertEquals
import org.junit.Test

class QuickAccessCardsTest {
    private val coffee = TestCards.loyalty
    private val bakery = TestCards.loyalty.copy(id = CardId("bakery"), title = "Bakery")
    private val books = TestCards.loyalty.copy(id = CardId("books"), title = "Books")

    @Test
    fun `favourites first, then most used, then by name`() {
        val cards = listOf(
            coffee.copy(useCount = 9),
            bakery.copy(isFavourite = true),
            books.copy(useCount = 2),
        )

        assertEquals(listOf("bakery", "loyalty-1", "books"), quickAccessCards(cards, limit = 4).map { it.id.value })
    }

    @Test
    fun `leaves out locked cards and non-loyalty cards and respects the limit`() {
        val cards = listOf(coffee.copy(isLocked = true), TestCards.visa, bakery, books)

        assertEquals(listOf("bakery"), quickAccessCards(cards, limit = 1).map { it.id.value })
    }

    @Test
    fun `cards found nearby come first, in the order they were found`() {
        val cards =
            listOf(coffee.copy(isFavourite = true), bakery, books, coffee.copy(id = CardId("locked"), isLocked = true))
        val nearby = listOf(CardId("books"), CardId("gone"), CardId("locked"), CardId("bakery"))

        assertEquals(
            listOf("books", "bakery", "loyalty-1"),
            quickAccessCards(cards, limit = 4, nearby = nearby).map { it.id.value },
        )
    }

    @Test
    fun `with a country, cards valid only in other countries are left out`() {
        val cards = listOf(
            coffee.copy(validity = CardValidity.of(listOf(code("DE")))),
            bakery,
            books.copy(validity = CardValidity.of(listOf(code("CH")))),
        )

        assertEquals(
            listOf("bakery", "books"),
            quickAccessCards(cards, limit = 4, country = code("CH")).map { it.id.value },
        )
    }

    @Test
    fun `a card valid in several countries is offered in each of them`() {
        val cards = listOf(coffee.copy(validity = CardValidity.of(listOf(code("AT"), code("CH")))))

        assertEquals(listOf("loyalty-1"), quickAccessCards(cards, limit = 4, country = code("AT")).map { it.id.value })
        assertEquals(listOf("loyalty-1"), quickAccessCards(cards, limit = 4, country = code("CH")).map { it.id.value })
        assertEquals(emptyList(), quickAccessCards(cards, limit = 4, country = code("DE")))
    }

    @Test
    fun `without a known country every card is offered`() {
        val cards = listOf(coffee.copy(validity = CardValidity.of(listOf(code("DE")))), bakery)

        assertEquals(2, quickAccessCards(cards, limit = 4, country = null).size)
        assertEquals(2, quickAccessCards(cards, limit = 4).size)
    }

    @Test
    fun `the country filter is applied before the limit`() {
        val cards = listOf(
            coffee.copy(isFavourite = true, validity = CardValidity.of(listOf(code("DE")))),
            bakery,
        )

        assertEquals(listOf("bakery"), quickAccessCards(cards, limit = 1, country = code("CH")).map { it.id.value })
    }

    private fun code(value: String) = requireNotNull(CountryCode.of(value))
}
