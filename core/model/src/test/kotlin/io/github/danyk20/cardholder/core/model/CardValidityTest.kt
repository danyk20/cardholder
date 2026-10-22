package io.github.danyk20.cardholder.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class CardValidityTest {
    private val ch = requireNotNull(CountryCode.of("CH"))
    private val de = requireNotNull(CountryCode.of("DE"))
    private val fr = requireNotNull(CountryCode.of("FR"))

    @Test
    fun `everywhere is valid in every country`() {
        assertTrue(CardValidity.Everywhere.isValidIn(ch))
        assertTrue(CardValidity.Everywhere.isValidIn(null))
    }

    @Test
    fun `restricted card is valid only in its countries`() {
        val validity = CardValidity.Countries(setOf(ch, de))

        assertTrue(validity.isValidIn(ch))
        assertTrue(validity.isValidIn(de))
        assertFalse(validity.isValidIn(fr))
    }

    @Test
    fun `unknown country never hides a card`() {
        assertTrue(CardValidity.Countries(setOf(ch)).isValidIn(null))
    }

    @Test
    fun `empty selection becomes everywhere`() {
        assertEquals(CardValidity.Everywhere, CardValidity.of(emptyList()))
    }

    @Test
    fun `non-empty selection becomes countries without duplicates`() {
        assertEquals(CardValidity.Countries(setOf(ch, de)), CardValidity.of(listOf(ch, de, ch)))
    }

    @Test
    fun `countries cannot be empty`() {
        assertFailsWith<IllegalArgumentException> { CardValidity.Countries(emptySet()) }
    }
}
