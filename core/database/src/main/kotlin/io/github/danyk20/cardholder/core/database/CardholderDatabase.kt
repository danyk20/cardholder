package io.github.danyk20.cardholder.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.danyk20.cardholder.core.database.dao.CardDao
import io.github.danyk20.cardholder.core.database.model.CardEntity

@Database(
    entities = [CardEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CardholderDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        const val NAME = "cardholder.db"
    }
}
