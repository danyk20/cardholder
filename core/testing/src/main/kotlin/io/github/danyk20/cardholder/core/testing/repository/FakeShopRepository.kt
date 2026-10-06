package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.repository.ShopRepository
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.Shop
import io.github.danyk20.cardholder.core.model.ShopLogo

const val MIGROS_LOGO_URL = "https://upload.wikimedia.org/migros.png"

class FakeShopRepository(
    private val shops: List<Shop> = listOf(
        Shop(
            id = "migros",
            name = "Migros Cumulus",
            brandColor = 0xFFFF6600,
            countries = setOf(CountryCode.of("CH")!!),
            defaultFormat = BarcodeFormat.EAN_13,
            logo = ShopLogo(MIGROS_LOGO_URL, "Public domain", "https://commons.wikimedia.org/wiki/File:Migros.svg"),
        ),
        Shop("lidl", "Lidl Plus", 0xFF0050AA, emptySet(), BarcodeFormat.QR_CODE),
    ),
) : ShopRepository {
    override suspend fun shops(): List<Shop> = shops

    override suspend fun shop(id: String): Shop? = shops.firstOrNull { it.id == id }
}
