package io.github.danyk20.cardholder.core.data.model

import io.github.danyk20.cardholder.core.database.model.CardEntity
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardNetwork
import io.github.danyk20.cardholder.core.model.CardSides
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.ImageRef
import java.time.Instant
import java.time.LocalDate

internal fun CardEntity.toCard(): Card = Card(
    id = CardId(id),
    title = title,
    color = enumValueOrNull<CardColor>(color) ?: CardColor.Default,
    info = toInfo(),
    sides = CardSides(front = frontImage?.let(::ImageRef), back = backImage?.let(::ImageRef)),
    logo = logoImage?.let(::ImageRef),
    isLocked = isLocked,
    hasCvv = sealedCvv != null,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    position = position,
    isFavourite = isFavourite,
    useCount = useCount,
    lastUsedAt = lastUsedAt?.let(Instant::ofEpochMilli),
    expiresOn = expiresOn?.let(LocalDate::parse),
)

private fun CardEntity.toInfo(): CardInfo = when (CardType.valueOf(type)) {
    CardType.BANK -> CardInfo.Bank(
        network = enumValueOrNull<CardNetwork>(bankNetwork) ?: CardNetwork.UNKNOWN,
        issuer = brandRef(bankId, bankName),
    )

    CardType.ID -> CardInfo.Id(checkNotNull(idCountry?.let(CountryCode::of)) { "ID card $id without country" })

    CardType.LOYALTY -> CardInfo.Loyalty(
        shop = brandRef(loyaltyShopId, loyaltyShopName) ?: BrandRef.Custom(""),
        format = enumValueOrNull<BarcodeFormat>(loyaltyBarcodeFormat) ?: BarcodeFormat.QR_CODE,
    )
}

private fun brandRef(id: String?, name: String?): BrandRef? = when {
    id != null -> BrandRef.Known(id, name.orEmpty())
    name != null -> BrandRef.Custom(name)
    else -> null
}

/** Columns derived from [CardInfo]. */
internal data class InfoColumns(
    val bankNetwork: String? = null,
    val bankId: String? = null,
    val bankName: String? = null,
    val idCountry: String? = null,
    val loyaltyShopId: String? = null,
    val loyaltyShopName: String? = null,
    val loyaltyBarcodeFormat: String? = null,
)

internal fun CardInfo.toColumns(): InfoColumns = when (this) {
    is CardInfo.Bank -> InfoColumns(
        bankNetwork = network.name,
        bankId = (issuer as? BrandRef.Known)?.id,
        bankName = issuer?.name,
    )

    is CardInfo.Id -> InfoColumns(idCountry = country.value)

    is CardInfo.Loyalty -> InfoColumns(
        loyaltyShopId = (shop as? BrandRef.Known)?.id,
        loyaltyShopName = shop.name,
        loyaltyBarcodeFormat = format.name,
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String?): T? =
    enumValues<T>().firstOrNull { it.name == name }
