package io.github.danyk20.cardholder.core.storage

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.model.ImageSource
import io.github.danyk20.cardholder.core.model.ImageRef
import io.github.danyk20.cardholder.core.security.EnvelopeCipher
import io.github.danyk20.cardholder.core.security.ProtectionLevel
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Encrypted storage of card side photos in the app's private files directory. */
@Singleton
class CardImageStore(
    private val files: EncryptedFileStore,
    private val decode: (ImageSource) -> ByteArray,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        cipher: EnvelopeCipher,
        normalizer: ImageNormalizer,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        files = EncryptedFileStore(File(context.filesDir, DIRECTORY), cipher),
        decode = { source ->
            when (source) {
                is ImageSource.Uri -> normalizer.fromUri(context.contentResolver, source.value.toUri())
                is ImageSource.Bytes -> normalizer.fromBytes(source.bytes)
            }
        },
        ioDispatcher = ioDispatcher,
    )

    /** Normalizes the image from [source], stores it encrypted with [level] and returns its reference. */
    suspend fun store(source: ImageSource, level: ProtectionLevel): ImageRef = withContext(ioDispatcher) {
        val ref = newRef()
        val jpeg = decode(source)
        try {
            files.write(ref.name, jpeg, level)
        } finally {
            jpeg.fill(0)
        }
        ref
    }

    /**
     * Stores a copy of [ref] protected with [level] and returns the new reference. Copying instead of
     * re-encrypting in place keeps the original intact until the caller commits the change.
     */
    suspend fun copy(ref: ImageRef, level: ProtectionLevel): ImageRef = withContext(ioDispatcher) {
        val copy = newRef()
        val jpeg = files.read(ref.name)
        try {
            files.write(copy.name, jpeg, level)
        } finally {
            jpeg.fill(0)
        }
        copy
    }

    /** Returns the decrypted JPEG bytes. */
    suspend fun read(ref: ImageRef): ByteArray = withContext(ioDispatcher) { files.read(ref.name) }

    suspend fun levelOf(ref: ImageRef): ProtectionLevel = withContext(ioDispatcher) { files.levelOf(ref.name) }

    suspend fun delete(ref: ImageRef) = withContext(ioDispatcher) { files.delete(ref.name) }

    private fun newRef() = ImageRef("${UUID.randomUUID()}.img")

    private companion object {
        const val DIRECTORY = "card_images"
    }
}
