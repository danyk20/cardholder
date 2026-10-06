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
    fun `finds a shop by id`() = runTest {
        val shop = repository(StandardTestDispatcher(testScheduler)).shop("migros")

        assertNotNull(shop)
        assertEquals("Migros Cumulus", shop.name)
    }
}
