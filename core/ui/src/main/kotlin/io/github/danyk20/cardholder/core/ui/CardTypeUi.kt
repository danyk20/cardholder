package io.github.danyk20.cardholder.core.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardType

val CardType.icon: ImageVector
    get() = when (this) {
        CardType.BANK -> CardholderIcons.BankCard
        CardType.ID -> CardholderIcons.IdCard
        CardType.LOYALTY -> CardholderIcons.LoyaltyCard
    }

@get:StringRes
val CardType.label: Int
    get() = when (this) {
        CardType.BANK -> R.string.card_type_bank
        CardType.ID -> R.string.card_type_id
        CardType.LOYALTY -> R.string.card_type_loyalty
    }

@get:StringRes
val CardType.description: Int
    get() = when (this) {
        CardType.BANK -> R.string.card_type_bank_description
        CardType.ID -> R.string.card_type_id_description
        CardType.LOYALTY -> R.string.card_type_loyalty_description
    }

@get:StringRes
val ValidationError.message: Int
    get() = when (this) {
        ValidationError.REQUIRED -> R.string.error_required
        ValidationError.INVALID_CHARACTERS -> R.string.error_invalid_characters
        ValidationError.INVALID_LENGTH -> R.string.error_invalid_length
        ValidationError.INVALID_CHECKSUM -> R.string.error_invalid_checksum
        ValidationError.INVALID_DATE -> R.string.error_invalid_date
    }

@get:StringRes
val CardColor.label: Int
    get() = when (this) {
        CardColor.NAVY -> R.string.color_navy
        CardColor.BLUE -> R.string.color_blue
        CardColor.TEAL -> R.string.color_teal
        CardColor.GREEN -> R.string.color_green
        CardColor.LIME -> R.string.color_lime
        CardColor.AMBER -> R.string.color_amber
        CardColor.ORANGE -> R.string.color_orange
        CardColor.RED -> R.string.color_red
        CardColor.PINK -> R.string.color_pink
        CardColor.PURPLE -> R.string.color_purple
        CardColor.BROWN -> R.string.color_brown
        CardColor.GRAPHITE -> R.string.color_graphite
        CardColor.WHITE -> R.string.color_white
        CardColor.SILVER -> R.string.color_silver
        CardColor.GRAY -> R.string.color_gray
        CardColor.SAND -> R.string.color_sand
        CardColor.LEMON -> R.string.color_lemon
        CardColor.MINT -> R.string.color_mint
        CardColor.SKY -> R.string.color_sky
        CardColor.LAVENDER -> R.string.color_lavender
        CardColor.BLUSH -> R.string.color_blush
    }
