package io.github.danyk20.cardholder.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CardholderDatabase::class.java,
    )

    @Test
    fun `migrating 1 to 2 keeps cards and adds an empty logo`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO cards (id, type, title, color, is_locked, created_at, updated_at, bank_network, id_country,
                    loyalty_shop_id, loyalty_shop_name, loyalty_barcode_format, front_image, back_image,
                    sealed_details, sealed_cvv)
                VALUES ('1', 'LOYALTY', 'Coffee', 'BROWN', 0, 1, 2, NULL, NULL, NULL, 'Corner', 'QR_CODE',
                    NULL, NULL, X'010203', NULL)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT title, logo_image FROM cards WHERE id = '1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Coffee", cursor.getString(0))
                assertNull(cursor.getString(1))
            }
        }
    }

    @Test
    fun `migrating 2 to 3 gives existing cards position 0`() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO cards (id, type, title, color, is_locked, created_at, updated_at, loyalty_shop_name,
                    loyalty_barcode_format, sealed_details)
                VALUES ('1', 'LOYALTY', 'Coffee', 'BROWN', 0, 1, 2, 'Corner', 'QR_CODE', X'010203')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT position FROM cards WHERE id = '1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `migrating 3 to 4 adds an empty issuing bank`() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO cards (id, type, title, color, is_locked, created_at, updated_at, bank_network, sealed_details)
                VALUES ('1', 'BANK', 'Visa', 'NAVY', 0, 1, 2, 'VISA', X'010203')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB, 4, true).use { db ->
            db.query("SELECT bank_network, bank_id, bank_name FROM cards WHERE id = '1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("VISA", cursor.getString(0))
                assertNull(cursor.getString(1))
                assertNull(cursor.getString(2))
            }
        }
    }

    @Test
    fun `migrating 4 to 5 adds favourites, usage and expiry columns`() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO cards (id, type, title, color, is_locked, created_at, updated_at, bank_network, sealed_details)
                VALUES ('1', 'BANK', 'Visa', 'NAVY', 0, 1, 2, 'VISA', X'010203')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB, 5, true).use { db ->
            val columns = "is_favourite, use_count, last_used_at, expires_on, expiry_reminded_for, travel_reminded_for"
            db.query("SELECT $columns FROM cards WHERE id = '1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
                assertNull(cursor.getString(2))
                assertNull(cursor.getString(3))
                assertNull(cursor.getString(4))
                assertNull(cursor.getString(5))
            }
        }
    }

    @Test
    fun `migrating 5 to 6 switches suggestions by location on for loyalty cards`() {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL(
                """
                INSERT INTO cards (id, type, title, color, is_locked, created_at, updated_at, bank_network,
                    loyalty_shop_name, loyalty_barcode_format, sealed_details)
                VALUES ('bank', 'BANK', 'Visa', 'NAVY', 0, 1, 2, 'VISA', NULL, NULL, X'010203'),
                    ('loyalty', 'LOYALTY', 'Coffee', 'BROWN', 0, 1, 2, NULL, 'Corner', 'QR_CODE', X'010203')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB, 6, true).use { db ->
            db.query("SELECT id, suggest_by_location FROM cards ORDER BY id").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("bank" to 0, cursor.getString(0) to cursor.getInt(1))
                assertTrue(cursor.moveToNext())
                assertEquals("loyalty" to 1, cursor.getString(0) to cursor.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM places").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        }
    }

    @Test
    fun `migrating 6 to 7 makes existing cards valid everywhere`() {
        helper.createDatabase(DB, 6).use { db ->
            db.execSQL(
                """
                INSERT INTO cards (id, type, title, color, is_locked, created_at, updated_at, bank_network,
                    loyalty_shop_name, loyalty_barcode_format, sealed_details)
                VALUES ('1', 'LOYALTY', 'Coffee', 'BROWN', 0, 1, 2, NULL, 'Corner', 'QR_CODE', X'010203')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB, 7, true).use { db ->
            db.query("SELECT title, valid_countries FROM cards WHERE id = '1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Coffee", cursor.getString(0))
                assertEquals("", cursor.getString(1))
            }
        }
    }

    @Test
    fun `migrating 7 to 8 keeps existing places learned, not pinned`() {
        helper.createDatabase(DB, 7).use { db ->
            db.execSQL(
                """
                INSERT INTO cards (id, type, title, color, is_locked, created_at, updated_at, bank_network,
                    loyalty_shop_name, loyalty_barcode_format, sealed_details, valid_countries)
                VALUES ('1', 'LOYALTY', 'Coffee', 'BROWN', 0, 1, 2, NULL, 'Corner', 'QR_CODE', X'010203', '')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO places (card_id, latitude, longitude, accuracy_m, created_at, last_used_at, use_count)
                VALUES ('1', 48.1486, 17.1077, 12.0, 1, 2, 3)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB, 8, true).use { db ->
            db.query("SELECT latitude, use_count, pinned FROM places").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(48.1486, cursor.getDouble(0), 0.0)
                assertEquals(3, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
