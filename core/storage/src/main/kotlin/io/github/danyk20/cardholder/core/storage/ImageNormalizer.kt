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

/** How an image is stored: card photos as JPEG, logos as PNG to keep transparency. */
enum class ImageKind(
    internal val maxDimensionPx: Int,
    internal val format: Bitmap.CompressFormat,
    internal val quality: Int,
) {
    /** Enough for sharp barcodes and text on an ID-1 card while keeping files around 300 KB. */
    PHOTO(maxDimensionPx = 2048, format = Bitmap.CompressFormat.JPEG, quality = 90),
    LOGO(maxDimensionPx = 512, format = Bitmap.CompressFormat.PNG, quality = 100),
}

/** Decodes images, applies EXIF rotation, downsizes them and re-encodes them for storage. */
class ImageNormalizer @Inject constructor() {
    fun fromUri(contentResolver: ContentResolver, uri: Uri, kind: ImageKind = ImageKind.PHOTO): ByteArray =
        normalize(ImageDecoder.createSource(contentResolver, uri), kind)

    fun fromBytes(bytes: ByteArray, kind: ImageKind = ImageKind.PHOTO): ByteArray =
        normalize(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), kind)

    private fun normalize(source: ImageDecoder.Source, kind: ImageKind): ByteArray {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longestSide = max(info.size.width, info.size.height)
            if (longestSide > kind.maxDimensionPx) {
                val scale = kind.maxDimensionPx.toFloat() / longestSide
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt(),
                    (info.size.height * scale).roundToInt(),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(kind.format, kind.quality, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
