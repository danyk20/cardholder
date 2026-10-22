package io.github.danyk20.cardholder.core.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class RegionTest {
    @Test
    fun `eu has 27 members`() = assertEquals(27, Region.EU.countries.size)

    @Test
    fun `schengen has 29 members`() = assertEquals(29, Region.SCHENGEN.countries.size)

    @Test
    fun `eurozone has 21 members`() = assertEquals(21, Region.EUROZONE.countries.size)

    @Test
    fun `eea has 30 members`() = assertEquals(30, Region.EEA.countries.size)

    @Test
    fun `schengen includes switzerland and excludes ireland`() {
        val codes = Region.SCHENGEN.countries.map { it.value }.toSet()
        assertTrue("CH" in codes)
        assertTrue("IE" !in codes)
    }

    @Test
    fun `every member is a valid country code`() {
        Region.entries.forEach { region ->
            region.countries.forEach { code ->
                assertEquals(code, CountryCode.of(code.value), "${region.name}: ${code.value}")
            }
        }
    }
}
