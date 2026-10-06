package io.github.danyk20.cardholder.core.data.model

import io.github.danyk20.cardholder.core.model.CardDetails
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Serialized (then sealed) form of [CardDetails]. Field names are part of the storage format. */
@Serializable
internal sealed interface CardDetailsDto {
    @Serializable
    @SerialName("bank")
    data class Bank(val number: String, val expiry: String, val holder: String) : CardDetailsDto

    @Serializable
    @SerialName("id")
    data class Id(val documentNumber: String? = null, val expiry: String? = null) : CardDetailsDto

    @Serializable
    @SerialName("loyalty")
    data class Loyalty(val code: String) : CardDetailsDto
}

internal val DetailsJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

internal fun CardDetails.toDto(): CardDetailsDto = when (this) {
    is CardDetails.Bank -> CardDetailsDto.Bank(number, expiry.toString(), holder)
    is CardDetails.Id -> CardDetailsDto.Id(documentNumber, expiry?.toString())
    is CardDetails.Loyalty -> CardDetailsDto.Loyalty(code)
}

internal fun CardDetailsDto.toModel(): CardDetails = when (this) {
    is CardDetailsDto.Bank -> CardDetails.Bank(number, YearMonth.parse(expiry), holder)
    is CardDetailsDto.Id -> CardDetails.Id(documentNumber, expiry?.let(LocalDate::parse))
    is CardDetailsDto.Loyalty -> CardDetails.Loyalty(code)
}

internal fun CardDetails.encode(): ByteArray =
    DetailsJson.encodeToString(CardDetailsDto.serializer(), toDto()).encodeToByteArray()

internal fun decodeCardDetails(bytes: ByteArray): CardDetails =
    DetailsJson.decodeFromString(CardDetailsDto.serializer(), bytes.decodeToString()).toModel()
