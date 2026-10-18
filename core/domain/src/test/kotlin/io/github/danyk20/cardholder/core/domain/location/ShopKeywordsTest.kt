package io.github.danyk20.cardholder.core.domain.location

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ShopKeywordsTest {
    @Test
    fun `takes the shop words of loyalty programme names`() {
        assertEquals(setOf("kaufland", "kauflandcard"), ShopKeywords.of("Kaufland Card"))
        assertEquals(setOf("migros", "cumulus", "migroscumulus"), ShopKeywords.of("Migros Cumulus"))
        assertEquals(setOf("shell", "shellgo"), ShopKeywords.of("Shell GO+"))
        assertEquals(setOf("muller", "mueller"), ShopKeywords.of("Müller"))
        assertEquals(emptySet(), ShopKeywords.of("H&M"))
    }

    @Test
    fun `adds the catalogue's extra names`() {
        assertTrue("dm" in ShopKeywords.of("dm-drogerie markt", wifiNames = setOf("dm")))
    }

    @Test
    fun `finds the shop as a whole word of network names`() {
        val kaufland = ShopKeywords.of("Kaufland Card")
        assertTrue(ShopKeywords.matches("Kaufland-Free", kaufland))
        assertTrue(ShopKeywords.matches("KauflandFree", kaufland))
        assertTrue(ShopKeywords.matches("KAUFLAND_Kunden", kaufland))
        assertFalse(ShopKeywords.matches("Kauflandia", kaufland))

        val mediaMarkt = ShopKeywords.of("MediaMarkt")
        assertTrue(ShopKeywords.matches("MediaMarktGuest", mediaMarkt))
        assertTrue(ShopKeywords.matches("Media Markt WLAN", ShopKeywords.of("Media Markt")))

        val migros = ShopKeywords.of("Migros Cumulus")
        assertTrue(ShopKeywords.matches("MigrosFreeWiFi", migros))
        assertTrue(ShopKeywords.matches("EDEKAGast", ShopKeywords.of("EDEKA")))
        assertTrue(ShopKeywords.matches("Mueller-WLAN", ShopKeywords.of("Müller")))
    }

    @Test
    fun `short names only match as whole words`() {
        val dm = ShopKeywords.of("dm-drogerie markt", wifiNames = setOf("dm"))

        assertTrue(ShopKeywords.matches("dm-Kunden-WLAN", dm))
        assertFalse(ShopKeywords.matches("admin", dm))
        assertFalse(ShopKeywords.matches("Edmund", dm))
    }

    @Test
    fun `matches nothing without keywords`() {
        assertFalse(ShopKeywords.matches("C&A", ShopKeywords.of("C&A")))
    }
}
