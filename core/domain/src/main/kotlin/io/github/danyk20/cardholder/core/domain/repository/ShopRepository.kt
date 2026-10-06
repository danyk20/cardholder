package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.model.Shop

interface ShopRepository {
    /** The bundled shop catalogue sorted by name. */
    suspend fun shops(): List<Shop>

    suspend fun shop(id: String): Shop?
}
