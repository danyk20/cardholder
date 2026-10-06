package io.github.danyk20.cardholder.core.storage

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes card photos, applies EXIF rotation, downsizes them and re-encodes them as JPEG. */
class ImageNormalizer @Inject constructor() {
    fun fromUri(contentResolver: ContentResolver, uri: Uri): ByteArray =
        normalize(ImageDecoder.createSource(contentResolver, uri))

    fun fromBytes(bytes: ByteArray): ByteArray = normalize(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))

    private fun normalize(source: ImageDecoder.Source): ByteArray {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longestSide = max(info.size.width, info.size.height)
            if (longestSide > MAX_DIMENSION_PX) {
                val scale = MAX_DIMENSION_PX.toFloat() / longestSide
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt(),
                    (info.size.height * scale).roundToInt(),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        /** Enough for sharp barcodes and text on an ID-1 card while keeping files around 300 KB. */
        const val MAX_DIMENSION_PX = 2048
        const val JPEG_QUALITY = 90
    }
}
