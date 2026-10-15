package io.github.danyk20.cardholder.core.data.share

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.repository.ShareFileStore
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Keeps at most the latest shared file, in the cache directory. Older ones are deleted, so card data
 * doesn't pile up after the receiving app has copied it.
 */
internal class CacheShareFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ShareFileStore {
    override suspend fun newShareFile(): String = withContext(ioDispatcher) {
        val dir = File(context.cacheDir, DIRECTORY)
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "cardholder-cards-${LocalDate.now(clock)}.$EXTENSION").apply { createNewFile() }
        FileProvider.getUriForFile(context, "${context.packageName}.share", file).toString()
    }

    private companion object {
        const val DIRECTORY = "share"
        const val EXTENSION = "cardholder"
    }
}
