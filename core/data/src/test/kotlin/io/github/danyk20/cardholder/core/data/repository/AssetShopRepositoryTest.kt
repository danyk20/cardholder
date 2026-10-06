package io.github.danyk20.cardholder.core.data.repository

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AssetShopRepositoryTest {
    private fun repository(dispatcher: TestDispatcher) =
        AssetShopRepository({ File("src/main/assets/shops.json").inputStream() }, dispatcher)

    @Test
    fun `bundled catalogue is valid`() = runTest {
        val shops = repository(StandardTestDispatcher(testScheduler)).shops()

        assertTrue(shops.size >= 50)
        assertEquals(shops.size, shops.map { it.id }.toSet().size, "shop ids must be unique")
        assertEquals(shops.sortedBy { it.name.lowercase() }, shops)
        shops.forEach { shop ->
            assertTrue(shop.name.isNotBlank())
            assertEquals(0xFF000000, shop.brandColor and 0xFF000000, "${shop.id} must be opaque")
        }
    }

    @Test
    fun `official logos come from allowed hosts with a licence and source`() = runTest {
        val logos = repository(StandardTestDispatcher(testScheduler)).shops().mapNotNull { it.logo }

        assertTrue(logos.size >= 50)
        logos.forEach { logo ->
            assertTrue(HttpsLogoDownloader.isAllowed(logo.url), logo.url)
            assertTrue(logo.license.isNotBlank())
            assertTrue(logo.sourcePage.startsWith("https://commons.wikimedia.org/"), logo.sourcePage)
            if (logo.license.startsWith("CC BY")) assertTrue(!logo.attribution.isNullOrBlank(), logo.url)
        }
    }

    @Test
    fun `bank catalogue is valid and its logos come from allowed hosts`() = runTest {
        val banks = AssetBankRepository(
            { File("src/main/assets/banks.json").inputStream() },
            StandardTestDispatcher(testScheduler),
        ).banks()

        assertTrue(banks.size >= 140)
        assertEquals(banks.size, banks.map { it.id }.toSet().size, "bank ids must be unique")
        banks.mapNotNull { it.logo }.forEach { logo ->
            assertTrue(HttpsLogoDownloader.isAllowed(logo.url), logo.url)
            if (logo.license.startsWith("CC BY")) assertTrue(!logo.attribution.isNullOrBlank(), logo.url)
        }
    }

    @Test
    fun `finds a shop by id`() = runTest {
        val shop = repository(StandardTestDispatcher(testScheduler)).shop("migros")

        assertNotNull(shop)
        assertEquals("Migros Cumulus", shop.name)
    }
}
