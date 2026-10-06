package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.model.details
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardNetwork
import io.github.danyk20.cardholder.core.model.CardSides
import io.github.danyk20.cardholder.core.model.ImageRef
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CardRepository] that simulates authentication-bound protection: CVVs and details of
 * locked cards are only readable while [isAuthenticated] is `true`.
 */
class FakeCardRepository : CardRepository {
    private data class Stored(val card: Card, val details: CardDetails, val cvv: String?)

    private val store = MutableStateFlow<Map<CardId, Stored>>(emptyMap())

    var isAuthenticated: Boolean = false
    var isKeyInvalidated: Boolean = false

    /** Cards whose stored data can't be read, e.g. because it's damaged. */
    val damaged = mutableSetOf<CardId>()
    var now: Instant = Instant.parse("2026-06-01T12:00:00Z")
    val savedDrafts = mutableListOf<CardDraft>()

    /** Adds a card at the end of the custom order, like a newly created card. */
    fun add(card: Card, details: CardDetails, cvv: String? = null) {
        store.update {
            val stored = card.copy(hasCvv = cvv != null, position = it.size, expiresOn = details.expiresOn)
            it + (card.id to Stored(stored, details, cvv))
        }
    }

    override fun observeCards(): Flow<List<Card>> =
        store.map { cards -> cards.values.map { it.card }.sortedBy { it.title.lowercase() } }

    override fun observeCard(id: CardId): Flow<Card?> = store.map { it[id]?.card }

    override suspend fun readDetails(id: CardId): SecureResult<CardDetails> {
        val stored = store.value[id] ?: error("No card $id")
        if (id in damaged) return SecureResult.Failed(IllegalStateException("Damaged card data"))
        return guarded(stored.card.isLocked) { stored.details }
    }

    override suspend fun readCvv(id: CardId): SecureResult<String?> {
        val stored = store.value[id] ?: error("No card $id")
        return guarded(protected = stored.cvv != null) { stored.cvv }
    }

    override suspend fun save(draft: CardDraft): SecureResult<CardId> {
        val existing = draft.id?.let { store.value[it] }
        if (existing != null && existing.card.isLocked && !isAuthenticated) return SecureResult.AuthenticationRequired
        savedDrafts += draft
        val id = draft.id ?: CardId.random()
        val content = draft.content
        val cvv = content.resolveCvv(existing?.cvv)
        val card = Card(
            id = id,
            title = draft.title,
            color = draft.color,
            info = content.toInfo(),
            sides = CardSides(
                front = draft.front.resolve(existing?.card?.sides?.front, "$id-front"),
                back = draft.back.resolve(existing?.card?.sides?.back, "$id-back"),
            ),
            logo = draft.logo.resolve(existing?.card?.logo, "$id-logo"),
            isLocked = draft.isLocked,
            hasCvv = cvv != null,
            createdAt = existing?.card?.createdAt ?: now,
            updatedAt = now,
            position = existing?.card?.position ?: ((store.value.values.maxOfOrNull { it.card.position } ?: -1) + 1),
            isFavourite = existing?.card?.isFavourite ?: false,
            useCount = existing?.card?.useCount ?: 0,
            lastUsedAt = existing?.card?.lastUsedAt,
            expiresOn = content.details.expiresOn,
        )
        store.update { it + (id to Stored(card, content.details, cvv)) }
        return SecureResult.Success(id)
    }

    override suspend fun setLocked(id: CardId, locked: Boolean): SecureResult<Unit> {
        val stored = store.value[id] ?: error("No card $id")
        return guarded(protected = !locked) {
            store.update { it + (id to stored.copy(card = stored.card.copy(isLocked = locked))) }
        }
    }

    override suspend fun reorder(ids: List<CardId>) {
        val others = store.value.values.map { it.card }.filter { it.id !in ids }.sortedBy { it.position }.map { it.id }
        store.update { cards ->
            (ids + others).mapIndexedNotNull { position, id ->
                cards[id]?.let { id to it.copy(card = it.card.copy(position = position)) }
            }.toMap()
        }
    }

    override suspend fun delete(id: CardId) {
        store.update { it - id }
    }

    /** Expiry dates the user was reminded of, by card. */
    val remindedExpiry = mutableMapOf<CardId, LocalDate>()

    override suspend fun setFavourite(id: CardId, favourite: Boolean) = updateCard(id) {
        it.copy(isFavourite = favourite)
    }

    override suspend fun recordUse(id: CardId) =
        updateCard(id) { it.copy(useCount = it.useCount + 1, lastUsedAt = now) }

    override suspend fun cardsDueForExpiryReminder(today: LocalDate, until: LocalDate): List<Card> =
        store.value.values.map { it.card }.filter { card ->
            val expiresOn = card.expiresOn ?: return@filter false
            expiresOn in today..until && remindedExpiry[card.id] != expiresOn
        }

    override suspend fun fillMissingExpiryDates() = Unit

    override suspend fun markExpiryReminded(id: CardId, expiresOn: LocalDate) {
        remindedExpiry[id] = expiresOn
    }

    private fun updateCard(id: CardId, transform: (Card) -> Card) = store.update { cards ->
        val stored = cards[id] ?: return@update cards
        cards + (id to stored.copy(card = transform(stored.card)))
    }

    private inline fun <T> guarded(protected: Boolean, block: () -> T): SecureResult<T> = when {
        !protected -> SecureResult.Success(block())
        isKeyInvalidated -> SecureResult.KeyInvalidated
        !isAuthenticated -> SecureResult.AuthenticationRequired
        else -> SecureResult.Success(block())
    }

    private fun CardContent.resolveCvv(current: String?): String? = when (this) {
        is CardContent.Bank -> when (val change = cvv) {
            CvvChange.Keep -> current
            CvvChange.Remove -> null
            is CvvChange.Set -> change.value
        }

        else -> null
    }

    private fun CardContent.toInfo(): CardInfo = when (this) {
        is CardContent.Bank -> CardInfo.Bank(CardNetwork.detect(details.number), issuer)
        is CardContent.Id -> CardInfo.Id(country)
        is CardContent.Loyalty -> CardInfo.Loyalty(shop, format)
    }

    private fun ImageChange.resolve(current: ImageRef?, newName: String): ImageRef? = when (this) {
        ImageChange.Keep -> current
        ImageChange.Remove -> null
        is ImageChange.Replace -> ImageRef(newName)
    }
}
