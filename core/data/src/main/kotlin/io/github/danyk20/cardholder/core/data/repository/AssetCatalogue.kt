package io.github.danyk20.cardholder.core.data.repository

import io.github.danyk20.cardholder.core.model.BrandLogo
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A JSON catalogue bundled in the assets, parsed once on first use and kept in memory. */
internal class AssetCatalogue<Dto, Model>(
    private val open: () -> InputStream,
    private val serializer: KSerializer<Dto>,
    private val ioDispatcher: CoroutineDispatcher,
    private val toModel: (Dto) -> Model,
    private val name: (Model) -> String,
) {
    private val mutex = Mutex()
    private var cache: List<Model>? = null

    suspend fun all(): List<Model> = mutex.withLock {
        cache ?: withContext(ioDispatcher) { load() }.also { cache = it }
    }

    private fun load(): List<Model> = open().bufferedReader().use { reader ->
        json.decodeFromString(ListSerializer(serializer), reader.readText())
            .map(toModel)
            .sortedBy { name(it).lowercase() }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Official logo entry of a catalogue item. */
@Serializable
internal data class LogoDto(
    val url: String,
    val license: String,
    val source: String,
    val attribution: String? = null,
) {
    fun toModel() = BrandLogo(url, license, source, attribution)
}

private const val HEX = 16
private const val OPAQUE = 0xFF000000

/** Parses `#RRGGBB` into an opaque `0xAARRGGBB` colour. */
internal fun parseBrandColor(hex: String): Long = OPAQUE or hex.removePrefix("#").toLong(HEX)
