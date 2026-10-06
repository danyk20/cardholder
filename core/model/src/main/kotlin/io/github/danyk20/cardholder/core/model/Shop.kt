package io.github.danyk20.cardholder.core.model

/** A shop from the bundled catalogue. */
data class Shop(
    val id: String,
    val name: String,
    /** Brand colour as `0xAARRGGBB`. */
    val brandColor: Long,
    val countries: Set<CountryCode>,
    val defaultFormat: BarcodeFormat,
    /** Freely licensed official logo that can be downloaded on request; `null` if none is known. */
    val logo: BrandLogo? = null,
)

/** Where an official shop logo can be downloaded from and under which licence. */
data class BrandLogo(
    val url: String,
    val license: String,
    /** Page describing the file, its author and licence. */
    val sourcePage: String,
    /** Required credit for licences such as CC BY-SA; `null` for public-domain logos. */
    val attribution: String? = null,
)

/** A shop or bank: either one from a bundled catalogue or a user-entered name. */
sealed interface BrandRef {
    val name: String

    data class Known(val id: String, override val name: String) : BrandRef

    data class Custom(override val name: String) : BrandRef
}
