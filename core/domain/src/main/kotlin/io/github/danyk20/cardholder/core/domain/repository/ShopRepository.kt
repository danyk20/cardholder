package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.model.Shop

interface ShopRepository {
    /** The bundled shop catalogue sorted by name. */
    suspend fun shops(): List<Shop>

    suspend fun shop(id: String): Shop?
}

/** Downloads official shop logos; the only network access of the app, and only on explicit request. */
interface LogoDownloader {
    /** Returns the encoded image, or `null` if it could not be downloaded or is not an allowed image. */
    suspend fun download(url: String): ByteArray?
}
