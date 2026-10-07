package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions") // The single entry point to stored cards.
interface CardRepository {
    /** All cards in no particular order; sorting and filtering happen in `ObserveCardsUseCase`. */
    fun observeCards(): Flow<List<Card>>

    fun observeCard(id: CardId): Flow<Card?>

    /** Decrypts the sensitive details. Locked cards require prior user authentication. */
    suspend fun readDetails(id: CardId): SecureResult<CardDetails>

    /** Decrypts the CVV of a bank card; always requires prior user authentication. `null` if none is stored. */
    suspend fun readCvv(id: CardId): SecureResult<String?>

    /** Creates or updates a card and returns its id. Updating a locked card requires prior authentication. */
    suspend fun save(draft: CardDraft): SecureResult<CardId>

    /** Locks or unlocks a card. Unlocking requires prior user authentication. */
    suspend fun setLocked(id: CardId, locked: Boolean): SecureResult<Unit>

    suspend fun delete(id: CardId)

    /** Stores [ids] as the custom order: the first card comes first. Cards not listed keep their place after them. */
    suspend fun reorder(ids: List<CardId>)

    suspend fun setFavourite(id: CardId, favourite: Boolean)

    /** Counts a use of the card (its code was shown or its details opened) for the "most used" order. */
    suspend fun recordUse(id: CardId)

    /**
     * Cards that expire between [today] and [until] (inclusive) and whose current expiry date hasn't
     * been reminded of yet.
     */
    suspend fun cardsDueForExpiryReminder(today: LocalDate, until: LocalDate): List<Card>

    /**
     * Stores the plain expiry date of unlocked cards saved before it was kept outside the encrypted
     * details. Locked cards get theirs the next time they're saved.
     */
    suspend fun fillMissingExpiryDates()

    /** Remembers that the user was reminded of [expiresOn]; a changed expiry date is reminded again. */
    suspend fun markExpiryReminded(id: CardId, expiresOn: LocalDate)
}
