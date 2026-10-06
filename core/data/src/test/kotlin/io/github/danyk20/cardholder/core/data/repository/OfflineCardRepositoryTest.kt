package io.github.danyk20.cardholder.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.database.CardholderDatabase
import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.model.ImageSource
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.model.getOrNull
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardNetwork
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.security.FakeKeyWrapper
import io.github.danyk20.cardholder.core.security.ProtectionLevel
import io.github.danyk20.cardholder.core.security.fakeEnvelopeCipher
import io.github.danyk20.cardholder.core.storage.CardImageStore
import io.github.danyk20.cardholder.core.storage.EncryptedFileStore
import io.github.danyk20.cardholder.core.testing.data.TestCards
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineCardRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val protectedKey = FakeKeyWrapper(requiresAuthentication = true)
    private val cipher = fakeEnvelopeCipher(protected = protectedKey)
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        CardholderDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val imageDir by lazy { folder.newFolder("images") }
    private val files by lazy { EncryptedFileStore(imageDir, cipher) }
    private val images by lazy {
        CardImageStore(
            files = files,
            decode = { source, _ -> (source as ImageSource.Bytes).bytes.copyOf() },
            ioDispatcher = Dispatchers.Unconfined,
        )
    }
    private val repository by lazy {
        OfflineCardRepository(
            dao = database.cardDao(),
            cipher = cipher,
            images = images,
            clock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private val bankDraft = CardDraft(
        title = "Visa",
        color = CardColor.BLUE,
        content = CardContent.Bank(TestCards.visaDetails, CvvChange.Set(TestCards.VISA_CVV)),
    )

    @After
    fun tearDown() = database.close()

    @Test
    fun `saves a bank card and exposes only non-sensitive info`() = runTest {
        val id = repository.save(bankDraft).getOrNull()!!

        val card = repository.observeCard(id).first()!!
        assertEquals(CardInfo.Bank(CardNetwork.VISA), card.info)
        assertTrue(card.hasCvv)
        assertFalse(card.isLocked)
        assertEquals(TestCards.visaDetails, repository.readDetails(id).getOrNull())
    }

    @Test
    fun `cvv always requires authentication`() = runTest {
        val id = repository.save(bankDraft).getOrNull()!!

        assertEquals(SecureResult.AuthenticationRequired, repository.readCvv(id))
        protectedKey.isAuthenticated = true
        assertEquals(SecureResult.Success(TestCards.VISA_CVV), repository.readCvv(id))
    }

    @Test
    fun `updating a card keeps the cvv unless changed`() = runTest {
        val id = repository.save(bankDraft).getOrNull()!!
        protectedKey.isAuthenticated = true

        repository.save(bankDraft.copy(id = id, content = CardContent.Bank(TestCards.visaDetails, CvvChange.Keep)))
        assertEquals(SecureResult.Success(TestCards.VISA_CVV), repository.readCvv(id))

        repository.save(bankDraft.copy(id = id, content = CardContent.Bank(TestCards.visaDetails, CvvChange.Remove)))
        assertEquals(SecureResult.Success(null), repository.readCvv(id))
    }

    @Test
    fun `locked card details and images require authentication`() = runTest {
        val id = repository.save(
            idDraft(isLocked = true, front = ImageChange.Replace(ImageSource.Bytes(FRONT))),
        ).getOrNull()!!
        val card = repository.observeCard(id).first()!!

        assertEquals(SecureResult.AuthenticationRequired, repository.readDetails(id))
        assertEquals(ProtectionLevel.PROTECTED, images.levelOf(card.sides.front!!))
        protectedKey.isAuthenticated = true
        assertEquals(TestCards.idCardDetails, repository.readDetails(id).getOrNull())
        assertContentEquals(FRONT, images.read(card.sides.front!!))
    }

    @Test
    fun `locking needs no authentication but unlocking does`() = runTest {
        val id = repository.save(idDraft(front = ImageChange.Replace(ImageSource.Bytes(FRONT)))).getOrNull()!!

        assertIs<SecureResult.Success<Unit>>(repository.setLocked(id, locked = true))
        assertTrue(repository.observeCard(id).first()!!.isLocked)
        assertEquals(SecureResult.AuthenticationRequired, repository.setLocked(id, locked = false))
        assertTrue(repository.observeCard(id).first()!!.isLocked)

        protectedKey.isAuthenticated = true
        assertIs<SecureResult.Success<Unit>>(repository.setLocked(id, locked = false))
        protectedKey.isAuthenticated = false

        val card = repository.observeCard(id).first()!!
        assertFalse(card.isLocked)
        assertEquals(TestCards.idCardDetails, repository.readDetails(id).getOrNull())
        assertContentEquals(FRONT, images.read(card.sides.front!!))
        assertEquals(1, imageDir.listFiles()!!.size, "obsolete image copies must be deleted")
    }

    @Test
    fun `replacing and removing images deletes old files`() = runTest {
        val id = repository.save(
            idDraft(
                front = ImageChange.Replace(ImageSource.Bytes(FRONT)),
                back = ImageChange.Replace(ImageSource.Bytes(BACK)),
            ),
        ).getOrNull()!!
        val original = repository.observeCard(id).first()!!.sides

        repository.save(
            idDraft(id = id, front = ImageChange.Replace(ImageSource.Bytes(BACK)), back = ImageChange.Remove),
        )

        val updated = repository.observeCard(id).first()!!.sides
        assertNull(updated.back)
        assertContentEquals(BACK, images.read(updated.front!!))
        assertFalse(files.exists(original.front!!.name))
        assertFalse(files.exists(original.back!!.name))
    }

    @Test
    fun `delete removes the card and its images`() = runTest {
        val id = repository.save(idDraft(front = ImageChange.Replace(ImageSource.Bytes(FRONT)))).getOrNull()!!

        repository.delete(id)

        assertNull(repository.observeCard(id).first())
        assertTrue(imageDir.listFiles()!!.isEmpty())
    }

    @Test
    fun `new cards are appended to the custom order and reordering persists`() = runTest {
        val first = repository.save(idDraft()).getOrNull()!!
        val second = repository.save(bankDraft).getOrNull()!!
        val third = repository.save(idDraft()).getOrNull()!!
        assertEquals(
            listOf(0, 1, 2),
            listOf(first, second, third).map {
                repository.observeCard(it).first()!!.position
            },
        )

        repository.reorder(listOf(third, first))

        assertEquals(
            listOf(third, first, second),
            repository.observeCards().first().sortedBy { it.position }.map { it.id },
        )
        // Editing a card keeps its place.
        repository.save(idDraft(id = third))
        assertEquals(0, repository.observeCard(third).first()!!.position)
    }

    private fun idDraft(
        id: CardId? = null,
        isLocked: Boolean = false,
        front: ImageChange = ImageChange.Keep,
        back: ImageChange = ImageChange.Keep,
    ) = CardDraft(
        id = id,
        title = "ID",
        color = CardColor.RED,
        content = CardContent.Id(CountryCode.of("CH")!!, TestCards.idCardDetails),
        front = front,
        back = back,
        isLocked = isLocked,
    )

    private companion object {
        val FRONT = "front".encodeToByteArray()
        val BACK = "back".encodeToByteArray()
    }
}
