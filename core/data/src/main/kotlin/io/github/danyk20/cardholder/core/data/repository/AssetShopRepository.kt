package io.github.danyk20.cardholder.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.repository.ShopRepository
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.Shop
import io.github.danyk20.cardholder.core.model.ShopLogo
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shop catalogue bundled as `assets/shops.json`. */
@Singleton
internal class AssetShopRepository(
    private val openCatalogue: () -> InputStream,
    private val ioDispatcher: CoroutineDispatcher,
) : ShopRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this({ context.assets.open(CATALOGUE) }, ioDispatcher)

    private val mutex = Mutex()
    private var cache: List<Shop>? = null

    override suspend fun shops(): List<Shop> = mutex.withLock {
        cache ?: withContext(ioDispatcher) { load() }.also { cache = it }
    }

    override suspend fun shop(id: String): Shop? = shops().firstOrNull { it.id == id }

    private fun load(): List<Shop> = openCatalogue().bufferedReader().use { reader ->
        json.decodeFromString<List<ShopDto>>(reader.readText())
            .map(ShopDto::toModel)
            .sortedBy { it.name.lowercase() }
    }

    @Serializable
    private data class ShopDto(
        val id: String,
        val name: String,
        val brandColor: String,
        val countries: List<String> = emptyList(),
        val defaultFormat: BarcodeFormat = BarcodeFormat.QR_CODE,
        val logo: LogoDto? = null,
    ) {
        fun toModel() = Shop(
            id = id,
            name = name,
            brandColor = OPAQUE or brandColor.removePrefix("#").toLong(HEX),
            countries = countries.mapNotNull(CountryCode::of).toSet(),
            defaultFormat = defaultFormat,
            logo = logo?.let { ShopLogo(it.url, it.license, it.source, it.attribution) },
        )
    }

    @Serializable
    private data class LogoDto(
        val url: String,
        val license: String,
        val source: String,
        val attribution: String? = null,
    )

    private companion object {
        const val CATALOGUE = "shops.json"
        const val HEX = 16
        const val OPAQUE = 0xFF000000
        val json = Json { ignoreUnknownKeys = true }
    }
}
