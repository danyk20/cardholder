package io.github.danyk20.cardholder.core.data.repository

import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.repository.CardImageRepository
import io.github.danyk20.cardholder.core.model.ImageRef
import io.github.danyk20.cardholder.core.security.secureCall
import io.github.danyk20.cardholder.core.storage.CardImageStore
import javax.inject.Inject

internal class OfflineCardImageRepository @Inject constructor(private val images: CardImageStore) :
    CardImageRepository {
    override suspend fun read(ref: ImageRef): SecureResult<ByteArray> = secureCall { images.read(ref) }
}
