package io.github.danyk20.cardholder.core.model

import java.util.UUID

/** Stable, globally unique identifier of a card. */
@JvmInline
value class CardId(val value: String) {
    init {
        require(value.isNotBlank()) { "CardId must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun random(): CardId = CardId(UUID.randomUUID().toString())
    }
}
