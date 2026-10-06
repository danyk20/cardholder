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

    private companion object {
        const val DB = "migration-test.db"
    }
}
