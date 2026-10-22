package io.github.danyk20.cardholder.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import io.github.danyk20.cardholder.core.designsystem.theme.CardAccentColors
import io.github.danyk20.cardholder.core.designsystem.theme.CardFaceColors
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.domain.repository.BankRepository
import io.github.danyk20.cardholder.core.domain.repository.CountryRepository
import io.github.danyk20.cardholder.core.domain.repository.ShopRepository
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardValidity
import java.text.Collator
import javax.inject.Inject

/** A [Card] with everything needed to render its face. */
@Immutable
data class CardSummary(
    val card: Card,
    val subtitle: String,
    /** Brand colour of a known shop as `0xAARRGGBB`; overrides the card colour. */
    val brandColor: Long? = null,
    /** The countries the card is valid in, sorted by name; empty when it is valid everywhere. */
    val validCountries: List<Country> = emptyList(),
) {
    val faceColors: CardFaceColors
        get() = brandColor?.let(CardFaceColors::fromArgb)
            ?: CardFaceColors.from(CardAccentColors.getValue(card.color.name))
}

/** Resolves shop colours and localized country names for cards. */
class CardSummaryFactory @Inject constructor(
    private val shopRepository: ShopRepository,
    private val bankRepository: BankRepository,
    private val countryRepository: CountryRepository,
) {
    suspend fun summarize(cards: List<Card>): List<CardSummary> {
        val shops = shopRepository.shops().associateBy { it.id }
        val banks = bankRepository.banks().associateBy { it.id }
        return cards.map { card ->
            when (val info = card.info) {
                is CardInfo.Bank -> CardSummary(
                    card = card,
                    subtitle = info.issuer?.name ?: info.network.displayName,
                    brandColor = (info.issuer as? BrandRef.Known)?.let { banks[it.id]?.brandColor },
                    validCountries = card.validCountries(),
                )

                is CardInfo.Id -> CardSummary(
                    card = card,
                    subtitle = "${info.country.flagEmoji} ${countryRepository.country(info.country)?.name.orEmpty()}",
                    validCountries = card.validCountries(),
                )

                is CardInfo.Loyalty -> CardSummary(
                    card = card,
                    subtitle = info.shop.name,
                    brandColor = (info.shop as? BrandRef.Known)?.let { shops[it.id]?.brandColor },
                    validCountries = card.validCountries(),
                )
            }
        }
    }

    suspend fun summarize(card: Card): CardSummary = summarize(listOf(card)).single()

    /** Names the card's countries; a code the catalogue lacks is shown as its code. */
    private fun Card.validCountries(): List<Country> = when (val validity = validity) {
        CardValidity.Everywhere -> emptyList()

        is CardValidity.Countries ->
            validity.countries
                .map { code -> countryRepository.country(code) ?: Country(code, code.value) }
                .sortedWith(compareBy<Country, String>(Collator.getInstance()) { it.name })
    }
}

@Composable
fun CardFace(
    summary: CardSummary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val card = summary.card
    CardFace(
        title = card.title,
        subtitle = summary.subtitle,
        type = card.type,
        colors = summary.faceColors,
        isLocked = card.isLocked,
        frontImage = card.sides.front,
        logo = card.logo,
        network = (card.info as? CardInfo.Bank)?.network,
        flag = (card.info as? CardInfo.Id)?.country?.flagEmoji,
        isFavourite = card.isFavourite,
        validCountries = summary.validCountries,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}
