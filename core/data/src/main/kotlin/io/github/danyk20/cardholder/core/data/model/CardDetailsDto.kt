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
    data class Bank(val number: String, val expiry: String, val holder: String, val notes: String = "") :
        CardDetailsDto

    @Serializable
    @SerialName("id")
    data class Id(val documentNumber: String? = null, val expiry: String? = null, val notes: String = "") :
        CardDetailsDto

    @Serializable
    @SerialName("loyalty")
    data class Loyalty(val code: String, val notes: String = "") : CardDetailsDto
}

internal val DetailsJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

internal fun CardDetails.toDto(): CardDetailsDto = when (this) {
    is CardDetails.Bank -> CardDetailsDto.Bank(number, expiry.toString(), holder, notes)
    is CardDetails.Id -> CardDetailsDto.Id(documentNumber, expiry?.toString(), notes)
    is CardDetails.Loyalty -> CardDetailsDto.Loyalty(code, notes)
}

internal fun CardDetailsDto.toModel(): CardDetails = when (this) {
    is CardDetailsDto.Bank -> CardDetails.Bank(number, YearMonth.parse(expiry), holder, notes)
    is CardDetailsDto.Id -> CardDetails.Id(documentNumber, expiry?.let(LocalDate::parse), notes)
    is CardDetailsDto.Loyalty -> CardDetails.Loyalty(code, notes)
}

internal fun CardDetails.encode(): ByteArray =
    DetailsJson.encodeToString(CardDetailsDto.serializer(), toDto()).encodeToByteArray()

internal fun decodeCardDetails(bytes: ByteArray): CardDetails =
    DetailsJson.decodeFromString(CardDetailsDto.serializer(), bytes.decodeToString()).toModel()
