package io.github.danyk20.cardholder.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.database.dao.CardDao
import io.github.danyk20.cardholder.core.database.model.CardEntity
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardDaoTest {
    private lateinit var database: CardholderDatabase
    private lateinit var dao: CardDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CardholderDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.cardDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `cards are ordered by title`() = runTest {
        dao.upsert(card("1", "Zoo"))
        dao.upsert(card("2", "apple"))
        dao.upsert(card("3", "Bank"))

        assertEquals(listOf("apple", "Bank", "Zoo"), dao.observeAll().first().map { it.title })
    }

    @Test
    fun `upsert replaces an existing card`() = runTest {
        dao.upsert(card("1", "Old"))
        dao.upsert(card("1", "New", sealedCvv = byteArrayOf(9)))

        val stored = dao.get("1")!!
        assertEquals("New", stored.title)
        assertEquals(card("1", "New", sealedCvv = byteArrayOf(9)), stored)
    }

    @Test
    fun `delete removes a card`() = runTest {
        dao.upsert(card("1", "Card"))

        dao.delete("1")

        assertNull(dao.get("1"))
        assertNull(dao.observe("1").first())
    }

    @Test
    fun `travel reminders are for IDs seven months ahead and fire once per expiry date`() = runTest {
        dao.upsert(card("id", "Passport").copy(type = "ID", idCountry = "CH", expiresOn = "2027-03-01"))
        dao.upsert(card("bank", "Visa").copy(type = "BANK", expiresOn = "2027-03-31"))
        val today = "2026-10-07"
        val inSevenMonths = "2027-05-07"
        val ids = listOf("ID")

        assertEquals(listOf("id"), dao.dueForTravelReminder(ids, today, inSevenMonths).map { it.id })
        assertEquals(emptyList(), dao.dueForExpiryReminder(listOf("BANK", "ID"), today, "2026-11-07"))

        dao.markTravelReminded("id", "2027-03-01")
        assertEquals(emptyList(), dao.dueForTravelReminder(ids, today, inSevenMonths))

        // A renewed document with a new expiry date is reminded of again.
        dao.setExpiresOn("id", "2027-04-30")
        assertEquals(listOf("id"), dao.dueForTravelReminder(ids, today, inSevenMonths).map { it.id })
    }

    private fun card(id: String, title: String, sealedCvv: ByteArray? = null) = CardEntity(
        id = id,
        type = "LOYALTY",
        title = title,
        color = "BLUE",
        isLocked = false,
        createdAt = 1,
        updatedAt = 2,
        bankNetwork = null,
        idCountry = null,
        loyaltyShopId = null,
        loyaltyShopName = "Shop",
        loyaltyBarcodeFormat = "QR_CODE",
        frontImage = null,
        backImage = null,
        sealedDetails = byteArrayOf(1, 2, 3),
        sealedCvv = sealedCvv,
    )
}
