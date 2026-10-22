package io.github.danyk20.cardholder.feature.cardeditor

import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.Region
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class CountrySelectionTest {
    private val ch = code("CH")
    private val at = code("AT")
    private val fr = code("FR")
    private val xk = code("XK")

    @Test
    fun `a region chip adds all members when none are selected`() {
        assertEquals(Region.EEA.countries, toggleRegion(emptySet(), Region.EEA))
    }

    @Test
    fun `a region chip adds the missing members when only some are selected`() {
        val selected = toggleRegion(setOf(ch), Region.SCHENGEN)

        assertEquals(Region.SCHENGEN.countries + ch, selected)
    }

    @Test
    fun `a region chip removes all members when they are all selected`() {
        val selected = toggleRegion(Region.EU.countries + ch, Region.EU)

        assertEquals(setOf(ch), selected)
    }

    @Test
    fun `tapping a region twice selects and then clears it`() {
        val once = toggleRegion(emptySet(), Region.EUROZONE)
        assertEquals(emptySet(), toggleRegion(once, Region.EUROZONE))
    }

    @Test
    fun `a region is selected only when all its members are`() {
        assertTrue(regionSelected(Region.EUROZONE.countries, Region.EUROZONE))
        assertFalse(regionSelected(Region.EUROZONE.countries - at, Region.EUROZONE))
    }

    @Test
    fun `all countries or no countries means everywhere`() {
        assertEquals(CardValidity.Everywhere, resolveValidity(allCountries = true, selected = setOf(ch)))
        assertEquals(CardValidity.Everywhere, resolveValidity(allCountries = false, selected = emptySet()))
    }

    @Test
    fun `a selection of countries is kept`() {
        assertEquals(
            CardValidity.Countries(setOf(ch, fr)),
            resolveValidity(allCountries = false, selected = setOf(ch, fr)),
        )
    }

    @Test
    fun `selected codes missing from the catalogue are still offered`() {
        val catalogue = listOf(Country(ch, "Switzerland"))

        val options = countryOptions(catalogue, setOf(ch, xk))

        assertEquals(listOf(Country(ch, "Switzerland"), Country(xk, "XK")), options)
    }

    @Test
    fun `the catalogue is offered unchanged when nothing is missing`() {
        val catalogue = listOf(Country(at, "Austria"), Country(ch, "Switzerland"))

        assertEquals(catalogue, countryOptions(catalogue, setOf(ch)))
    }

    @Test
    fun `the selected countries of a card are its countries`() {
        assertEquals(emptySet(), CardValidity.Everywhere.selectedCountries())
        assertEquals(setOf(ch, at), CardValidity.Countries(setOf(ch, at)).selectedCountries())
    }

    private fun code(value: String) = requireNotNull(CountryCode.of(value))
}
