package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.repository.ShopRepository
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.Shop

class FakeShopRepository(
    private val shops: List<Shop> = listOf(
        Shop("migros", "Migros Cumulus", 0xFFFF6600, setOf(CountryCode.of("CH")!!), BarcodeFormat.EAN_13),
        Shop("lidl", "Lidl Plus", 0xFF0050AA, emptySet(), BarcodeFormat.QR_CODE),
    ),
) : ShopRepository {
    override suspend fun shops(): List<Shop> = shops

    override suspend fun shop(id: String): Shop? = shops.firstOrNull { it.id == id }
}
