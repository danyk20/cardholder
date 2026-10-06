package io.github.danyk20.cardholder.image

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.repository.CardImageRepository
import io.github.danyk20.cardholder.core.model.ImageRef
import okio.Buffer

/** Lets Coil load encrypted card photos referenced by [ImageRef]. */
class CardImageFetcher(
    private val ref: ImageRef,
    private val options: Options,
    private val repository: CardImageRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult = when (val result = repository.read(ref)) {
        is SecureResult.Success -> SourceFetchResult(
            source = ImageSource(Buffer().write(result.value), options.fileSystem),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )

        SecureResult.AuthenticationRequired -> throw ProtectedImageException("Authentication required")

        SecureResult.KeyInvalidated -> throw ProtectedImageException("Key invalidated")
    }

    class Factory(private val repository: CardImageRepository) : Fetcher.Factory<ImageRef> {
        override fun create(data: ImageRef, options: Options, imageLoader: ImageLoader): Fetcher =
            CardImageFetcher(data, options, repository)
    }

    /** Memory-cache key; image files are immutable, so the name identifies the content. */
    class RefKeyer : Keyer<ImageRef> {
        override fun key(data: ImageRef, options: Options): String = data.name
    }
}

class ProtectedImageException(message: String) : Exception(message)
