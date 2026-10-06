package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.repository.BankRepository
import io.github.danyk20.cardholder.core.model.Bank
import io.github.danyk20.cardholder.core.model.BrandLogo
import io.github.danyk20.cardholder.core.model.CountryCode

const val UBS_LOGO_URL = "https://upload.wikimedia.org/ubs.png"

class FakeBankRepository(
    private val banks: List<Bank> = listOf(
        Bank(
            id = "ubs",
            name = "UBS",
            brandColor = 0xFFE60000,
            countries = setOf(CountryCode.of("CH")!!),
            logo = BrandLogo(UBS_LOGO_URL, "Public domain", "https://commons.wikimedia.org/wiki/File:UBS.svg"),
        ),
        Bank(id = "neon", name = "neon", brandColor = 0xFF1E1E1E, countries = setOf(CountryCode.of("CH")!!)),
    ),
) : BankRepository {
    override suspend fun banks(): List<Bank> = banks

    override suspend fun bank(id: String): Bank? = banks.firstOrNull { it.id == id }
}
