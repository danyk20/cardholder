package io.github.danyk20.cardholder.quickaccess

import io.github.danyk20.cardholder.core.model.CardId
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
}
