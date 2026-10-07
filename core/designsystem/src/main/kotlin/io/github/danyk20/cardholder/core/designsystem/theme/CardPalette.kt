package io.github.danyk20.cardholder.core.designsystem.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Colours of a card face rendered without a photo. Card faces look the same in light and dark theme. */
data class CardFaceColors(val start: Color, val end: Color, val content: Color) {
    val brush: Brush get() = Brush.linearGradient(listOf(start, end))

    companion object {
        /** Text on light faces; not pure black, which looks harsh on coloured cards. */
        private val DarkContent = Color(0xFF1A1A1A)

        /** Builds a face from a single brand/accent colour. */
        fun from(color: Color): CardFaceColors {
            // Light colours only darken slightly towards the corner, so they keep looking light.
            val end = lerp(color, Color.Black, if (color.luminance() > LIGHT_LUMINANCE) 0.12f else 0.35f)
            // Text must be readable across the whole gradient, so contrast is judged on its middle.
            val middle = lerp(color, end, 0.5f)
            return CardFaceColors(
                start = color,
                end = end,
                content = if (contrast(DarkContent, middle) >
                    contrast(Color.White, middle)
                ) {
                    DarkContent
                } else {
                    Color.White
                },
            )
        }

        /** WCAG contrast ratio between two colours (1 to 21). */
        private fun contrast(a: Color, b: Color): Float {
            val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (lighter + 0.05f) / (darker + 0.05f)
        }

        private const val LIGHT_LUMINANCE = 0.4f

        fun fromArgb(argb: Long): CardFaceColors = from(Color(argb))
    }
}

/** Accent colours offered to the user, keyed by `CardColor.name`. */
val CardAccentColors: Map<String, Color> = linkedMapOf(
    "NAVY" to Color(0xFF1B3A5C),
    "BLUE" to Color(0xFF1E6FD9),
    "TEAL" to Color(0xFF00796B),
    "GREEN" to Color(0xFF2E7D32),
    "LIME" to Color(0xFF6B7A16),
    "AMBER" to Color(0xFFFFB300),
    "ORANGE" to Color(0xFFC63F17),
    "RED" to Color(0xFFC62828),
    "PINK" to Color(0xFFAD1457),
    "PURPLE" to Color(0xFF6A1B9A),
    "BROWN" to Color(0xFF5D4037),
    "GRAPHITE" to Color(0xFF37474F),
    // Light colours
    "WHITE" to Color(0xFFF7F7F5),
    "SILVER" to Color(0xFFCFD8DC),
    "GRAY" to Color(0xFFB0B0B0),
    "SAND" to Color(0xFFE8D5B5),
    "LEMON" to Color(0xFFFFF59D),
    "MINT" to Color(0xFFB2DFDB),
    "SKY" to Color(0xFFBBDEFB),
    "LAVENDER" to Color(0xFFD1C4E9),
    "BLUSH" to Color(0xFFF8BBD0),
)
