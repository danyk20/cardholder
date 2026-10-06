package io.github.danyk20.cardholder.core.data.backup

import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.model.ImportStrategy
import io.github.danyk20.cardholder.core.domain.model.getOrNull
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

    private class InMemoryDocuments : DocumentAccess {
        val files = mutableMapOf<String, ByteArray>()

        override fun read(uri: String): ByteArray = files[uri] ?: throw java.io.IOException("missing")

        override fun write(uri: String, bytes: ByteArray) {
            files[uri] = bytes
        }
    }
}
