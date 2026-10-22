package io.github.danyk20.cardholder.core.ui

import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeBankRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeCountryRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeShopRepository
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CardSummaryFactoryTest {
    private val factory = CardSummaryFactory(FakeShopRepository(), FakeBankRepository(), FakeCountryRepository())

    @Test
    fun `restricted cards name their countries sorted by name`() = runTest {
        val card = TestCards.loyalty.copy(validity = CardValidity.of(listOf(code("SK"), code("AT"), code("CH"))))

        assertEquals(
            listOf("Austria", "Slovakia", "Switzerland"),
            factory.summarize(card).validCountries.map(Country::name),
        )
    }

    @Test
    fun `cards valid everywhere name no countries`() = runTest {
        assertEquals(emptyList(), factory.summarize(TestCards.loyalty).validCountries)
    }

    @Test
    fun `a code the catalogue lacks is named by its code`() = runTest {
        val card = TestCards.loyalty.copy(validity = CardValidity.of(listOf(code("DE"))))

        assertEquals(listOf(Country(code("DE"), "DE")), factory.summarize(card).validCountries)
    }

    private fun code(value: String) = requireNotNull(CountryCode.of(value))
}
