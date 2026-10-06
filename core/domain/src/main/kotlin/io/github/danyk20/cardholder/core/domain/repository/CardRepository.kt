package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    /** All cards sorted by title. */
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
}
