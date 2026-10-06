package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.validation.BankCardValidator
import io.github.danyk20.cardholder.core.domain.validation.CardDraftValidator
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.CardId
import javax.inject.Inject

sealed interface SaveCardResult {
    data class Saved(val id: CardId) : SaveCardResult

    data class Invalid(val errors: Map<CardField, ValidationError>) : SaveCardResult

    data object AuthenticationRequired : SaveCardResult

    data object KeyInvalidated : SaveCardResult
}

/** Normalizes and validates a [CardDraft], then persists it. */
class SaveCardUseCase @Inject constructor(
    private val cardRepository: CardRepository,
    private val validator: CardDraftValidator,
) {
    suspend operator fun invoke(draft: CardDraft): SaveCardResult {
        val normalized = draft.normalized()
        val errors = validator.validate(normalized)
        if (errors.isNotEmpty()) return SaveCardResult.Invalid(errors)
        return when (val result = cardRepository.save(normalized)) {
            is SecureResult.Success -> SaveCardResult.Saved(result.value)
            SecureResult.AuthenticationRequired -> SaveCardResult.AuthenticationRequired
            SecureResult.KeyInvalidated -> SaveCardResult.KeyInvalidated
        }
    }

    private fun CardDraft.normalized(): CardDraft = copy(
        title = title.trim(),
        content = when (val content = content) {
            is CardContent.Bank -> content.copy(
                details = content.details.copy(
                    number = BankCardValidator.normalizeNumber(content.details.number),
                    holder = content.details.holder.trim(),
                ),
                cvv = content.cvv.trimmed(),
            )

            is CardContent.Id -> content.copy(
                details = content.details.copy(
                    documentNumber = content.details.documentNumber?.trim()?.takeIf(String::isNotEmpty),
                ),
            )

            is CardContent.Loyalty -> content.copy(
                details = content.details.copy(code = normalizeCode(content.details.code, content.format)),
            )
        },
    )

    private fun CvvChange.trimmed(): CvvChange = when (this) {
        is CvvChange.Set -> CvvChange.Set(value.trim())
        else -> this
    }

    private fun normalizeCode(code: String, format: BarcodeFormat): String {
        val trimmed = code.trim()
        return if (format in UPPERCASE_FORMATS) trimmed.uppercase() else trimmed
    }

    private companion object {
        val UPPERCASE_FORMATS = setOf(BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODABAR)
    }
}
