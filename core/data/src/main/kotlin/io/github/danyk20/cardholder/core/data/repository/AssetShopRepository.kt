package io.github.danyk20.cardholder.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.repository.ShopRepository
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.Shop
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Serializable

/** Shop catalogue bundled as `assets/shops.json`. */
@Singleton
internal class AssetShopRepository(openCatalogue: () -> InputStream, ioDispatcher: CoroutineDispatcher) :
    ShopRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this({ context.assets.open("shops.json") }, ioDispatcher)

    private val catalogue = AssetCatalogue(openCatalogue, ShopDto.serializer(), ioDispatcher, ShopDto::toModel) {
        it.name
    }

    override suspend fun shops(): List<Shop> = catalogue.all()

    override suspend fun shop(id: String): Shop? = shops().firstOrNull { it.id == id }

    @Serializable
    private data class ShopDto(
        val id: String,
        val name: String,
        val brandColor: String,
        val countries: List<String> = emptyList(),
        val defaultFormat: BarcodeFormat = BarcodeFormat.QR_CODE,
        val logo: LogoDto? = null,
        val wifiNames: Set<String> = emptySet(),
    ) {
        fun toModel() = Shop(
            id = id,
            name = name,
            brandColor = parseBrandColor(brandColor),
            countries = countries.mapNotNull(CountryCode::of).toSet(),
            defaultFormat = defaultFormat,
            logo = logo?.toModel(),
            wifiNames = wifiNames,
        )
    }
}
