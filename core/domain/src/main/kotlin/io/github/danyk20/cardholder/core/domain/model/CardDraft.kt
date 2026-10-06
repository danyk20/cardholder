package io.github.danyk20.cardholder.core.domain.model

import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.ShopRef

/** Everything needed to create a new card or update an existing one. */
data class CardDraft(
    /** `null` creates a new card. */
    val id: CardId? = null,
    val title: String,
    val color: CardColor,
    val content: CardContent,
    val front: ImageChange = ImageChange.Keep,
    val back: ImageChange = ImageChange.Keep,
    val logo: ImageChange = ImageChange.Keep,
    val isLocked: Boolean = false,
)

/** Type-specific content of a [CardDraft]. */
sealed interface CardContent {
    data class Bank(val details: CardDetails.Bank, val cvv: CvvChange = CvvChange.Keep) : CardContent

    data class Id(val country: CountryCode, val details: CardDetails.Id) : CardContent

    data class Loyalty(val shop: ShopRef, val format: BarcodeFormat, val details: CardDetails.Loyalty) : CardContent
}

val CardContent.details: CardDetails
    get() = when (this) {
        is CardContent.Bank -> details
        is CardContent.Id -> details
        is CardContent.Loyalty -> details
    }

sealed interface CvvChange {
    data object Keep : CvvChange

    data object Remove : CvvChange

    data class Set(val value: String) : CvvChange {
        override fun toString(): String = "Set(██)"
    }
}

sealed interface ImageChange {
    data object Keep : ImageChange

    data object Remove : ImageChange

    data class Replace(val source: ImageSource) : ImageChange
}

/** Where a new card image comes from. */
sealed interface ImageSource {
    /** A `content://` or `file://` URI, e.g. from the document scanner or the photo picker. */
    data class Uri(val value: String) : ImageSource

    /** Raw encoded image bytes, e.g. from a backup. */
    class Bytes(val bytes: ByteArray) : ImageSource
}
