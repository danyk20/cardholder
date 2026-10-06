package io.github.danyk20.cardholder.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.danyk20.cardholder.core.database.model.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
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
}
