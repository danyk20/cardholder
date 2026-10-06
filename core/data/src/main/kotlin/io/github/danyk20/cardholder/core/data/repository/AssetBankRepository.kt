package io.github.danyk20.cardholder.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.repository.BankRepository
import io.github.danyk20.cardholder.core.model.Bank
import io.github.danyk20.cardholder.core.model.CountryCode
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Serializable

/** Bank catalogue bundled as `assets/banks.json`. */
@Singleton
internal class AssetBankRepository(openCatalogue: () -> InputStream, ioDispatcher: CoroutineDispatcher) :
    BankRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this({ context.assets.open("banks.json") }, ioDispatcher)

    private val catalogue = AssetCatalogue(openCatalogue, BankDto.serializer(), ioDispatcher, BankDto::toModel) {
        it.name
    }

    override suspend fun banks(): List<Bank> = catalogue.all()

    override suspend fun bank(id: String): Bank? = banks().firstOrNull { it.id == id }

    @Serializable
    private data class BankDto(
        val id: String,
        val name: String,
        val brandColor: String,
        val countries: List<String> = emptyList(),
        val logo: LogoDto? = null,
    ) {
        fun toModel() = Bank(
            id = id,
            name = name,
            brandColor = parseBrandColor(brandColor),
            countries = countries.mapNotNull(CountryCode::of).toSet(),
            logo = logo?.toModel(),
        )
    }
}
