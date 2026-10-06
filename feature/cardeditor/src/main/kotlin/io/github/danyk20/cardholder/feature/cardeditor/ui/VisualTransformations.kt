package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import io.github.danyk20.cardholder.core.model.CardNetwork

/** Groups card digits the way they are printed: 4-6-5 for American Express, blocks of four otherwise. */
internal class CardNumberTransformation(private val network: CardNetwork) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val groups = if (network == CardNetwork.AMERICAN_EXPRESS) AMEX_GROUPS else null
        val breaks = breakPositions(text.length, groups)
        val formatted = buildString {
            text.text.forEachIndexed { index, char ->
                if (index in breaks) append(' ')
                append(char)
            }
        }
        return TransformedText(AnnotatedString(formatted), SeparatorOffsetMapping(breaks))
    }

    private fun breakPositions(length: Int, groups: List<Int>?): Set<Int> {
        if (groups == null) return (GROUP until length step GROUP).toSet()
        return groups.runningReduce(Int::plus).dropLast(1).filter { it < length }.toSet()
    }

    private companion object {
        const val GROUP = 4
        val AMEX_GROUPS = listOf(4, 6, 5)
    }
}

/** Shows `MMYY` digits as `MM/YY`. */
internal object ExpiryTransformation : VisualTransformation {
    private const val SLASH_POSITION = 2

    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = if (text.length > SLASH_POSITION) {
            text.text.substring(0, SLASH_POSITION) + "/" + text.text.substring(SLASH_POSITION)
        } else {
            text.text
        }
        return TransformedText(AnnotatedString(formatted), SeparatorOffsetMapping(setOf(SLASH_POSITION)))
    }
}

/** Offset mapping for transformations that insert one separator character before each index in [breaks]. */
private class SeparatorOffsetMapping(private val breaks: Set<Int>) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = offset + breaks.count { it < offset }

    override fun transformedToOriginal(offset: Int): Int {
        var original = 0
        var transformed = 0
        while (transformed < offset) {
            if (original in breaks && transformed < offset) transformed++
            if (transformed >= offset) break
            original++
            transformed++
        }
        return original
    }
}
