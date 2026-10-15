package io.github.danyk20.cardholder.core.data.backup

import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.model.ImportStrategy
import io.github.danyk20.cardholder.core.domain.model.SharedCardAction
import io.github.danyk20.cardholder.core.domain.model.SharedCardChoice
import io.github.danyk20.cardholder.core.domain.model.SharedFilePreview
import io.github.danyk20.cardholder.core.domain.model.getOrNull
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.ImageRef
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeCardImageRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeDeviceSecurity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OfflineBackupRepositoryTest {
    private val documents = InMemoryDocuments()
    private val password = "a good password".toCharArray()

    private fun repository(
        cards: FakeCardRepository,
        images: FakeCardImageRepository = FakeCardImageRepository(),
        deviceSecurity: FakeDeviceSecurity = FakeDeviceSecurity(),
    ) = OfflineBackupRepository(
        cardRepository = cards,
        imageRepository = images,
        deviceSecurity = deviceSecurity,
        documents = documents,
        codec = BackupCodec(iterations = 1_000),
        clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun sourceCards() = FakeCardRepository().apply {
        add(
            TestCards.visa.copy(sides = TestCards.visa.sides.copy(front = ImageRef("visa-front"))),
            TestCards.visaDetails,
            TestCards.VISA_CVV,
        )
        add(TestCards.idCard, TestCards.idCardDetails)
        add(TestCards.loyalty, TestCards.loyaltyDetails)
    }

    @Test
    fun `export needs authentication when cards are protected`() = runTest {
        val result = repository(sourceCards()).export("backup", password)

        assertEquals(BackupResult.AuthenticationRequired, result)
    }

    @Test
    fun `exported cards can be imported on another device`() = runTest {
        val source = sourceCards().apply { isAuthenticated = true }
        val images = FakeCardImageRepository(mutableMapOf(ImageRef("visa-front") to byteArrayOf(7, 7)))
        assertEquals(BackupResult.Exported(3), repository(source, images).export("backup", password))

        val target = FakeCardRepository()
        assertEquals(
            BackupResult.Imported(imported = 3, skipped = 0),
            repository(target).import("backup", password, ImportStrategy.SKIP_EXISTING),
        )

        target.isAuthenticated = true
        val imported = target.observeCards().first().associateBy { it.id }
        assertEquals(setOf(TestCards.visa.id, TestCards.idCard.id, TestCards.loyalty.id), imported.keys)
        assertTrue(imported.getValue(TestCards.idCard.id).isLocked)
        assertEquals(TestCards.VISA_CVV, target.readCvv(TestCards.visa.id).getOrNull())
        assertEquals(TestCards.loyaltyDetails, target.readDetails(TestCards.loyalty.id).getOrNull())
        val visaDraft = target.savedDrafts.first { it.id == TestCards.visa.id }
        assertIs<ImageChange.Replace>(visaDraft.front)
        assertEquals(ImageChange.Remove, visaDraft.back)
        assertEquals(CvvChange.Set(TestCards.VISA_CVV), (visaDraft.content as CardContent.Bank).cvv)
    }

    @Test
    fun `existing cards are skipped or replaced`() = runTest {
        val source = sourceCards().apply { isAuthenticated = true }
        val images = FakeCardImageRepository(mutableMapOf(ImageRef("visa-front") to byteArrayOf(1)))
        repository(source, images).export("backup", password)
        val target = FakeCardRepository().apply {
            isAuthenticated = true
            add(TestCards.loyalty.copy(title = "Old title"), TestCards.loyaltyDetails)
        }

        assertEquals(
            BackupResult.Imported(2, 1),
            repository(target).import("backup", password, ImportStrategy.SKIP_EXISTING),
        )
        assertEquals("Old title", target.observeCard(TestCards.loyalty.id).first()!!.title)

        assertEquals(
            BackupResult.Imported(3, 0),
            repository(target).import("backup", password, ImportStrategy.REPLACE_EXISTING),
        )
        assertEquals(TestCards.loyalty.title, target.observeCard(TestCards.loyalty.id).first()!!.title)
    }

    @Test
    fun `wrong password and invalid files are reported`() = runTest {
        repository(
            FakeCardRepository().apply {
                add(TestCards.loyalty, TestCards.loyaltyDetails)
            },
        ).export("backup", password)
        documents.files["garbage"] = "hello".repeat(20).encodeToByteArray()

        assertEquals(
            BackupResult.WrongPassword,
            repository(FakeCardRepository()).import("backup", "nope".toCharArray(), ImportStrategy.SKIP_EXISTING),
        )
        assertEquals(
            BackupResult.InvalidFile,
            repository(FakeCardRepository()).import("garbage", password, ImportStrategy.SKIP_EXISTING),
        )
    }

    @Test
    fun `protected cards need a screen lock on the target device`() = runTest {
        val source = sourceCards().apply { isAuthenticated = true }
        repository(
            source,
            FakeCardImageRepository(mutableMapOf(ImageRef("visa-front") to byteArrayOf(1))),
        ).export("backup", password)

        val result = repository(FakeCardRepository(), deviceSecurity = FakeDeviceSecurity(isSecure = false))
            .import("backup", password, ImportStrategy.SKIP_EXISTING)

        assertEquals(BackupResult.DeviceNotSecure, result)
    }

    @Test
    fun `shared cards can be sent without a password and include CVV and notes`() = runTest {
        val source = sourceCards().apply { isAuthenticated = true }
        val images = FakeCardImageRepository(mutableMapOf(ImageRef("visa-front") to byteArrayOf(1)))
        assertEquals(
            BackupResult.Exported(1),
            repository(source, images).exportCards(listOf(TestCards.visa.id), "share", password = null),
        )

        val target = FakeCardRepository()
        val preview = assertIs<SharedFilePreview.Cards>(repository(target).preview("share", password = null))
        assertEquals(listOf(TestCards.visa.title), preview.cards.map { it.title })
        assertEquals(null, preview.cards.single().duplicateOf)

        val choices = mapOf(TestCards.visa.id.value to SharedCardChoice(SharedCardAction.ADD_NEW, locked = true))
        assertEquals(BackupResult.Imported(1, 0), repository(target).importShared("share", null, choices))
        target.isAuthenticated = true
        val imported = target.observeCards().first().single()
        assertTrue(imported.isLocked)
        assertEquals(TestCards.VISA_CVV, target.readCvv(imported.id).getOrNull())
        assertEquals(TestCards.visaDetails, target.readDetails(imported.id).getOrNull())
    }

    @Test
    fun `an encrypted shared file asks for its password`() = runTest {
        val source = FakeCardRepository().apply { add(TestCards.loyalty, TestCards.loyaltyDetails) }
        repository(source).exportCards(listOf(TestCards.loyalty.id), "share", "eight888".toCharArray())

        val target = repository(FakeCardRepository())
        assertEquals(SharedFilePreview.Unreadable(BackupResult.PasswordRequired), target.preview("share", null))
        assertEquals(
            SharedFilePreview.Unreadable(BackupResult.WrongPassword),
            target.preview("share", "wrong".toCharArray()),
        )
        assertIs<SharedFilePreview.Cards>(target.preview("share", "eight888".toCharArray()))
    }

    @Test
    fun `duplicates are found by number and can be added, replaced or skipped`() = runTest {
        val source = FakeCardRepository().apply { add(TestCards.loyalty, TestCards.loyaltyDetails) }
        repository(source).exportCards(listOf(TestCards.loyalty.id), "share", null)
        val existingId = CardId("someone-elses-id")
        val target = FakeCardRepository().apply {
            add(TestCards.loyalty.copy(id = existingId, title = "My copy"), TestCards.loyaltyDetails)
        }
        val key = TestCards.loyalty.id.value
        val preview = assertIs<SharedFilePreview.Cards>(repository(target).preview("share", null))
        assertEquals(existingId, preview.cards.single().duplicateOf)

        repository(target).importShared("share", null, mapOf(key to SharedCardChoice(SharedCardAction.SKIP, false)))
        assertEquals(listOf("My copy"), target.observeCards().first().map { it.title })

        repository(target).importShared("share", null, mapOf(key to SharedCardChoice(SharedCardAction.REPLACE, false)))
        assertEquals(listOf(TestCards.loyalty.title), target.observeCards().first().map { it.title })
        assertEquals(existingId, target.observeCards().first().single().id)

        repository(target).importShared("share", null, mapOf(key to SharedCardChoice(SharedCardAction.ADD_NEW, false)))
        assertEquals(2, target.observeCards().first().size)
    }

    @Test
    fun `shared files leave out the sender's favourites`() = runTest {
        val source = FakeCardRepository().apply {
            add(TestCards.loyalty, TestCards.loyaltyDetails)
            setFavourite(TestCards.loyalty.id, true)
        }
        repository(source).exportCards(listOf(TestCards.loyalty.id), "share", null)

        val target = FakeCardRepository()
        repository(target).import("share", null, ImportStrategy.SKIP_EXISTING)
        assertEquals(false, target.observeCards().first().single().isFavourite)
    }

    private class InMemoryDocuments : DocumentAccess {
        val files = mutableMapOf<String, ByteArray>()

        override fun read(uri: String): ByteArray = files[uri] ?: throw java.io.IOException("missing")

        override fun write(uri: String, bytes: ByteArray) {
            files[uri] = bytes
        }
    }
}
