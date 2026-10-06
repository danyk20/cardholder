package io.github.danyk20.cardholder.core.model

/** A bank from the bundled catalogue that issues payment cards. */
data class Bank(
    val id: String,
    val name: String,
    /** Brand colour as `0xAARRGGBB`. */
    val brandColor: Long,
    /** Countries where the bank mainly operates; empty for international banks. */
    val countries: Set<CountryCode>,
    /** Freely licensed official logo that can be downloaded on request; `null` if none is known. */
    val logo: BrandLogo? = null,
)
