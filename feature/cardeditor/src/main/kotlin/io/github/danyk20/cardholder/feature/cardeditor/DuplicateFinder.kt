package io.github.danyk20.cardholder.feature.cardeditor

import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardInfo

/**
 * The existing card a new [draft] probably duplicates: same shop, same bank, or same name. Only the plain,
 * non-sensitive columns are compared, so locked cards don't need unlocking.
 */
internal fun findDuplicate(cards: List<Card>, draft: CardDraft): DuplicateWarning? {
    val content = draft.content
    val sameBrand = when (content) {
        is CardContent.Loyalty -> cards.firstOrNull { card ->
            (card.info as? CardInfo.Loyalty)?.shop?.let { it.sameBrandAs(content.shop) } == true
        }?.let { DuplicateWarning(DuplicateReason.SAME_SHOP, it.title) }

        is CardContent.Bank -> content.issuer?.let { issuer ->
            cards.firstOrNull { card -> (card.info as? CardInfo.Bank)?.issuer?.sameBrandAs(issuer) == true }
        }?.let { DuplicateWarning(DuplicateReason.SAME_BANK, it.title) }

        is CardContent.Id -> null
    }
    return sameBrand ?: cards.firstOrNull { it.title.trim().equals(draft.title.trim(), ignoreCase = true) }
        ?.let { DuplicateWarning(DuplicateReason.SAME_NAME, it.title) }
}

private fun BrandRef.sameBrandAs(other: BrandRef): Boolean = when {
    this is BrandRef.Known && other is BrandRef.Known -> id == other.id
    else -> name.isNotBlank() && name.trim().equals(other.name.trim(), ignoreCase = true)
}
