package io.github.danyk20.cardholder.core.model

/** Reference to an encrypted image of a card side stored by the app. */
@JvmInline
value class ImageRef(val name: String) {
    init {
        require(name.isNotBlank()) { "ImageRef must not be blank" }
    }
}

/** Photos of both sides of a card. Capturing them is optional. */
data class CardSides(val front: ImageRef? = null, val back: ImageRef? = null) {
    val refs: List<ImageRef> get() = listOfNotNull(front, back)

    companion object {
        val None = CardSides()
    }
}

enum class CardSide {
    FRONT,
    BACK,
}
