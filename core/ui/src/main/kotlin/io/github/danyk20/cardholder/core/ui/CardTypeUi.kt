package io.github.danyk20.cardholder.core.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
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
