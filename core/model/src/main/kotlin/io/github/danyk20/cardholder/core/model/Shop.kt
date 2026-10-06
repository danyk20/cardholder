package io.github.danyk20.cardholder.core.model

/** A shop from the bundled catalogue. */
data class Shop(
    val id: String,
    val name: String,
    /** Brand colour as `0xAARRGGBB`. */
    val brandColor: Long,
    val countries: Set<CountryCode>,
    val defaultFormat: BarcodeFormat,
)

/** The shop a loyalty card belongs to: either one from the catalogue or a user-entered name. */
sealed interface ShopRef {
    val name: String

    data class Known(val id: String, override val name: String) : ShopRef

    data class Custom(override val name: String) : ShopRef
}
