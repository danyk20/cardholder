package io.github.danyk20.cardholder.core.barcode

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.common.BitMatrix
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders [content] as a barcode, as large as fits into the available space. Square symbols (QR,
 * Aztec, Data Matrix) are drawn square, linear codes and PDF417 as a wide strip. Always black on
 * white; when the height is unbounded the code fills the width.
 */
@Composable
fun BarcodeImage(content: String, format: BarcodeFormat, contentDescription: String, modifier: Modifier = Modifier) {
    val aspectRatio = when {
        format.isSquare -> 1f
        format == BarcodeFormat.PDF_417 -> PDF417_ASPECT_RATIO
        else -> LINEAR_ASPECT_RATIO
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = if (constraints.hasBoundedHeight) minOf(maxWidth, maxHeight * aspectRatio) else maxWidth
        val widthPx = with(LocalDensity.current) { width.roundToPx() }
        val heightPx = (widthPx / aspectRatio).toInt()
        val bitmap by produceState<ImageBitmap?>(null, content, format, widthPx) {
            value = withContext(Dispatchers.Default) {
                BarcodeEncoder.encode(content, format, widthPx, heightPx)?.toImageBitmap()
            }
        }
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // Nearest-neighbour scaling keeps module edges sharp.
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .width(width)
                    .aspectRatio(aspectRatio)
                    .semantics { this.contentDescription = contentDescription },
            )
        }
    }
}

private fun BitMatrix.toImageBitmap(): ImageBitmap {
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
            pixels[offset + x] = if (get(x, y)) BLACK else WHITE
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val LINEAR_ASPECT_RATIO = 2.6f
private const val PDF417_ASPECT_RATIO = 3f
