package io.github.danyk20.cardholder.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.danyk20.cardholder.core.database.model.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // One function per query the app needs.
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY title COLLATE LOCALIZED ASC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    fun observe(id: String): Flow<CardEntity?>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: String): CardEntity?

    @Query("SELECT * FROM cards ORDER BY title COLLATE LOCALIZED ASC")
    suspend fun getAll(): List<CardEntity>

    @Upsert
    suspend fun upsert(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM cards")
    suspend fun maxPosition(): Int

    @Query("UPDATE cards SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("UPDATE cards SET is_favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: String, favourite: Boolean)

    @Query("UPDATE cards SET use_count = use_count + 1, last_used_at = :now WHERE id = :id")
    suspend fun recordUse(id: String, now: Long)

    /** Dates are ISO `yyyy-MM-dd` strings, so they compare correctly as text. */
    @Query(
        """
        SELECT * FROM cards
        WHERE type IN (:types) AND expires_on IS NOT NULL AND expires_on BETWEEN :today AND :until
            AND (expiry_reminded_for IS NULL OR expiry_reminded_for != expires_on)
        """,
    )
    suspend fun dueForExpiryReminder(types: List<String>, today: String, until: String): List<CardEntity>

    @Query(
        """
        SELECT * FROM cards
        WHERE type IN (:types) AND expires_on IS NOT NULL AND expires_on BETWEEN :today AND :until
            AND (travel_reminded_for IS NULL OR travel_reminded_for != expires_on)
        """,
    )
    suspend fun dueForTravelReminder(types: List<String>, today: String, until: String): List<CardEntity>

    @Query("UPDATE cards SET travel_reminded_for = :expiresOn WHERE id = :id")
    suspend fun markTravelReminded(id: String, expiresOn: String)

    /** Unlocked bank and ID cards saved before expiry dates were stored in plain columns. */
    @Query("SELECT * FROM cards WHERE expires_on IS NULL AND type IN ('BANK', 'ID') AND is_locked = 0")
    suspend fun missingExpiry(): List<CardEntity>

    @Query("UPDATE cards SET expires_on = :expiresOn WHERE id = :id")
    suspend fun setExpiresOn(id: String, expiresOn: String?)

    @Query("UPDATE cards SET expiry_reminded_for = :expiresOn WHERE id = :id")
    suspend fun markExpiryReminded(id: String, expiresOn: String)

    /** Assigns positions 0, 1, 2, … in the order of [ids]. */
    @Transaction
    suspend fun reorder(ids: List<String>) {
        ids.forEachIndexed { position, id -> setPosition(id, position) }
    }
}
