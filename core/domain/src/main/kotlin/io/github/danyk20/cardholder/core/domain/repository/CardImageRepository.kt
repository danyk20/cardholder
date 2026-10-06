package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.model.ImageRef

interface CardImageRepository {
    /** Returns the decrypted, encoded (JPEG) image. Images of locked cards require prior authentication. */
    suspend fun read(ref: ImageRef): SecureResult<ByteArray>
}
