package io.github.danyk20.cardholder.core.designsystem.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Colours of a card face rendered without a photo. Card faces look the same in light and dark theme. */
data class CardFaceColors(val start: Color, val end: Color, val content: Color) {
    val brush: Brush get() = Brush.linearGradient(listOf(start, end))

    companion object {
        /** Builds a face from a single brand/accent colour. */
        fun from(color: Color): CardFaceColors {
            val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
            return CardFaceColors(
                start = color,
                end = lerp(color, Color.Black, 0.35f),
                content = if (luminance > 0.6f) Color(0xFF1A1A1A) else Color.White,
            )
        }

        fun fromArgb(argb: Long): CardFaceColors = from(Color(argb))
    }
}

/** Accent colours offered to the user, keyed by `CardColor.name`. */
val CardAccentColors: Map<String, Color> = linkedMapOf(
    "NAVY" to Color(0xFF1B3A5C),
    "BLUE" to Color(0xFF1E6FD9),
    "TEAL" to Color(0xFF00897B),
    "GREEN" to Color(0xFF2E7D32),
    "LIME" to Color(0xFF9E9D24),
    "AMBER" to Color(0xFFFFB300),
    "ORANGE" to Color(0xFFEF6C00),
    "RED" to Color(0xFFC62828),
    "PINK" to Color(0xFFAD1457),
    "PURPLE" to Color(0xFF6A1B9A),
    "BROWN" to Color(0xFF5D4037),
    "GRAPHITE" to Color(0xFF37474F),
)
