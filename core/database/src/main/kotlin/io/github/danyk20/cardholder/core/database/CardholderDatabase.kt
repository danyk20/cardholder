package io.github.danyk20.cardholder.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.danyk20.cardholder.core.database.dao.CardDao
import io.github.danyk20.cardholder.core.database.dao.PlaceDao
import io.github.danyk20.cardholder.core.database.model.CardEntity
import io.github.danyk20.cardholder.core.database.model.PlaceAccessPointEntity
import io.github.danyk20.cardholder.core.database.model.PlaceEntity
import io.github.danyk20.cardholder.core.database.model.PlaceFixEntity
import io.github.danyk20.cardholder.core.database.model.TravellingAccessPointEntity

@Database(
    entities = [
        CardEntity::class,
        PlaceEntity::class,
        PlaceFixEntity::class,
        PlaceAccessPointEntity::class,
        TravellingAccessPointEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        // 1 -> 2: card logos
        AutoMigration(from = 1, to = 2),
        // 2 -> 3: custom card order
        AutoMigration(from = 2, to = 3),
        // 3 -> 4: issuing bank of bank cards
        AutoMigration(from = 3, to = 4),
        // 4 -> 5: favourites, usage counts and expiry reminders
        AutoMigration(from = 4, to = 5),
        // 5 -> 6: places where cards are used, for suggestions by location
        AutoMigration(from = 5, to = 6, spec = CardholderDatabase.SuggestLoyaltyCardsByLocation::class),
        // 6 -> 7: countries a card is valid in; existing cards are valid everywhere
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class CardholderDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    abstract fun placeDao(): PlaceDao

    /** Loyalty cards start with suggestions by location switched on, other cards off. */
    class SuggestLoyaltyCardsByLocation : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE cards SET suggest_by_location = 1 WHERE type = 'LOYALTY'")
        }
    }

    companion object {
        const val NAME = "cardholder.db"
    }
}
