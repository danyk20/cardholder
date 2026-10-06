package io.github.danyk20.cardholder.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.danyk20.cardholder.core.database.dao.CardDao
import io.github.danyk20.cardholder.core.database.model.CardEntity

@Database(
    entities = [CardEntity::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // 1 -> 2: card logos
        AutoMigration(from = 1, to = 2),
        // 2 -> 3: custom card order
        AutoMigration(from = 2, to = 3),
        // 3 -> 4: issuing bank of bank cards
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class CardholderDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        const val NAME = "cardholder.db"
    }
}
