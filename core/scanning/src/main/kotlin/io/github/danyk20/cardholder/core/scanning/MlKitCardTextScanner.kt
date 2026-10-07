package io.github.danyk20.cardholder.core.scanning

import android.content.Context
import androidx.core.net.toUri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.repository.CardTextScanner
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Recognises text on card photos with ML Kit (Latin script; the model is provided by Google Play
 * services and runs on the device). Failures just mean nothing could be read.
 */
internal class MlKitCardTextScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CardTextScanner {
    override suspend fun readLines(uri: String): List<String> {
        val image = withContext(ioDispatcher) {
            try {
                InputImage.fromFilePath(context, uri.toUri())
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
        } ?: return emptyList()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val text = recognizer.process(image).awaitOrNull() ?: return emptyList()
            text.textBlocks
                .sortedBy { it.boundingBox?.top ?: 0 }
                .flatMap { block -> block.lines.map { it.text } }
        } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
            emptyList()
        } finally {
            recognizer.close()
        }
    }
}
