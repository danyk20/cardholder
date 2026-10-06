package io.github.danyk20.cardholder.feature.cardeditor

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.repository.ScannedBarcode
import io.github.danyk20.cardholder.core.domain.usecase.SaveCardUseCase
import io.github.danyk20.cardholder.core.domain.validation.CardDraftValidator
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.CardSide
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.ShopRef
import io.github.danyk20.cardholder.core.testing.MainDispatcherRule
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeBarcodeImageScanner
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeCountryRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeDeviceSecurity
import io.github.danyk20.cardholder.core.testing.repository.FakeShopRepository
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeCardRepository()
    private val deviceSecurity = FakeDeviceSecurity()
    private val barcodeScanner = FakeBarcodeImageScanner()

    private fun viewModel(cardId: String? = null) = CardEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("cardId" to cardId)),
        cardRepository = repository,
        saveCard = SaveCardUseCase(repository, CardDraftValidator()),
        shopRepository = FakeShopRepository(),
        countryRepository = FakeCountryRepository(),
        deviceSecurity = deviceSecurity,
        validator = CardDraftValidator(),
        barcodeImageScanner = barcodeScanner,
    )

    @Test
    fun `new card walks through type, sides and details`() {
        val viewModel = viewModel()
        assertEquals(EditorStep.TYPE, viewModel.uiState.value.step)

        viewModel.onTypeSelected(CardType.LOYALTY)
        assertEquals(EditorStep.SIDES, viewModel.uiState.value.step)
        viewModel.onSidesDone()
        assertEquals(EditorStep.DETAILS, viewModel.uiState.value.step)

        assertTrue(viewModel.onBack())
        assertEquals(EditorStep.SIDES, viewModel.uiState.value.step)
        assertTrue(viewModel.onBack())
        assertFalse(viewModel.onBack())
    }

    @Test
    fun `saves a bank card with cvv and a default title`() = runTest {
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.BANK)
        viewModel.onSidesDone()
        viewModel.onNumberChange("4111 1111 1111 1111")
        viewModel.onExpiryChange("04/30")
        viewModel.onHolderChange("Jane Doe")
        viewModel.onCvvChange("123")
        viewModel.onSideImagePicked(CardSide.FRONT, "content://front")

        viewModel.onSave()

        assertNotNull(viewModel.uiState.value.savedCardId)
        val draft = repository.savedDrafts.single()
        assertEquals("Visa •••• 1111", draft.title)
        val content = assertIs<CardContent.Bank>(draft.content)
        assertEquals(YearMonth.of(2030, 4), content.details.expiry)
        assertEquals(CvvChange.Set("123"), content.cvv)
        assertIs<ImageChange.Replace>(draft.front)
        assertEquals(ImageChange.Keep, draft.back)
    }

    @Test
    fun `shows all validation errors at once`() {
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.BANK)
        viewModel.onNumberChange("4111111111111112")
        viewModel.onExpiryChange("13/30")

        viewModel.onSave()

        assertEquals(
            mapOf(
                CardField.NUMBER to ValidationError.INVALID_CHECKSUM,
                CardField.EXPIRY to ValidationError.INVALID_DATE,
                CardField.HOLDER to ValidationError.REQUIRED,
            ),
            viewModel.uiState.value.errors,
        )
        assertTrue(repository.savedDrafts.isEmpty())

        viewModel.onHolderChange("Jane")
        assertNull(viewModel.uiState.value.errors[CardField.HOLDER])
    }

    @Test
    fun `selecting a known shop presets the barcode format`() = runTest {
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.LOYALTY)

        viewModel.onShopSelected(FakeShopRepository().shop("migros")!!)

        val loyalty = viewModel.uiState.value.loyalty
        assertEquals(ShopRef.Known("migros", "Migros Cumulus"), loyalty.shop)
        assertEquals(BarcodeFormat.EAN_13, loyalty.format)
        assertEquals("Migros Cumulus", viewModel.uiState.value.defaultTitle)
    }

    @Test
    fun `barcode on a loyalty card photo fills an empty code`() {
        barcodeScanner.barcodes["content://back"] = ScannedBarcode("4006381333931", BarcodeFormat.EAN_13)
        barcodeScanner.barcodes["content://other"] = ScannedBarcode("OTHER", BarcodeFormat.QR_CODE)
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.LOYALTY)

        viewModel.onSideImagePicked(CardSide.BACK, "content://back")
        viewModel.onSideImagePicked(CardSide.FRONT, "content://other")

        assertEquals(
            LoyaltyForm(code = "4006381333931", format = BarcodeFormat.EAN_13),
            viewModel.uiState.value.loyalty,
        )
    }

    @Test
    fun `locking requires a secure device`() {
        deviceSecurity.isSecure = false
        val viewModel = viewModel()

        viewModel.onLockedChange(true)

        assertFalse(viewModel.uiState.value.isLocked)
        assertFalse(viewModel.uiState.value.canProtect)
    }

    @Test
    fun `editing a locked card asks for authentication and then loads it`() = runTest {
        repository.add(TestCards.idCard, TestCards.idCardDetails)

        val viewModel = viewModel(TestCards.idCard.id.value)

        assertEquals(LoadState.AUTHENTICATION_REQUIRED, viewModel.uiState.value.loadState)
        assertEquals(AuthPurpose.LOAD, viewModel.uiState.value.pendingAuthentication)

        repository.isAuthenticated = true
        viewModel.onAuthenticationResult(AuthPurpose.LOAD, succeeded = true)

        val state = viewModel.uiState.value
        assertEquals(LoadState.READY, state.loadState)
        assertEquals(EditorStep.DETAILS, state.step)
        assertEquals("CH", state.id.country?.code?.value)
        assertEquals("C1234567", state.id.documentNumber)
        assertTrue(state.isLocked)
    }

    @Test
    fun `editing keeps the stored cvv unless changed`() = runTest {
        repository.add(TestCards.visa, TestCards.visaDetails, cvv = TestCards.VISA_CVV)
        val viewModel = viewModel(TestCards.visa.id.value)
        assertTrue(viewModel.uiState.value.bank.hasStoredCvv)

        viewModel.onSave()

        val content = assertIs<CardContent.Bank>(repository.savedDrafts.single().content)
        assertEquals(CvvChange.Keep, content.cvv)
    }
}
