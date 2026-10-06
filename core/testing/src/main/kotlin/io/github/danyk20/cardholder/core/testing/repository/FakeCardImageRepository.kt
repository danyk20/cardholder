package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.repository.CardImageRepository
import io.github.danyk20.cardholder.core.model.ImageRef

class FakeCardImageRepository(val images: MutableMap<ImageRef, ByteArray> = mutableMapOf()) : CardImageRepository {
    override suspend fun read(ref: ImageRef): SecureResult<ByteArray> =
        images[ref]?.let { SecureResult.Success(it) } ?: error("No image $ref")
}
