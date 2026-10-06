package io.github.danyk20.cardholder.core.domain.validation

import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.model.CardNetwork
import javax.inject.Inject

/** Validates a complete [CardDraft] before it is persisted. */
class CardDraftValidator
@Inject
constructor() {
    fun validate(draft: CardDraft): Map<CardField, ValidationError> = buildMap {
        if (draft.title.isBlank()) put(CardField.TITLE, ValidationError.REQUIRED)
        when (val content = draft.content) {
            is CardContent.Bank -> {
                val details = content.details
                BankCardValidator.validateNumber(details.number)?.let { put(CardField.NUMBER, it) }
                BankCardValidator.validateHolder(details.holder)?.let { put(CardField.HOLDER, it) }
                val cvv = content.cvv
                if (cvv is CvvChange.Set) {
                    val network = CardNetwork.detect(details.number)
                    val cvvError = if (cvv.value.isEmpty()) {
                        ValidationError.REQUIRED
                    } else {
                        BankCardValidator.validateCvv(cvv.value, network)
                    }
                    cvvError?.let { put(CardField.CVV, it) }
                }
            }

            is CardContent.Id -> Unit

            is CardContent.Loyalty -> {
                if (content.shop.name.isBlank()) put(CardField.SHOP, ValidationError.REQUIRED)
                BarcodeValidator.validate(content.details.code, content.format)?.let { put(CardField.CODE, it) }
            }
        }
    }
}
