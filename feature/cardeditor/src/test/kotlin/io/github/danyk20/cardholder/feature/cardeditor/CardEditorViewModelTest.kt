package io.github.danyk20.cardholder.feature.cardeditor

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.model.ImageSource
import io.github.danyk20.cardholder.core.domain.repository.ScannedBarcode
import io.github.danyk20.cardholder.core.domain.usecase.SaveCardUseCase
import io.github.danyk20.cardholder.core.domain.validation.CardDraftValidator
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.CardSide
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.testing.MainDispatcherRule
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeBankRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeBarcodeImageScanner
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeCardTextScanner
import io.github.danyk20.cardholder.core.testing.repository.FakeCountryRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeDeviceSecurity
import io.github.danyk20.cardholder.core.testing.repository.FakeLogoDownloader
import io.github.danyk20.cardholder.core.testing.repository.FakeShopRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeUserPreferencesRepository
import io.github.danyk20.cardholder.core.testing.repository.MIGROS_LOGO_URL
import io.github.danyk20.cardholder.core.testing.repository.UBS_LOGO_URL
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertContentEquals
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
    private val textScanner = FakeCardTextScanner()
    private val logoDownloader = FakeLogoDownloader()

    private fun viewModel(cardId: String? = null) = CardEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("cardId" to cardId)),
        cardRepository = repository,
        saveCard = SaveCardUseCase(repository, CardDraftValidator()),
        catalogues = EditorCatalogues(FakeShopRepository(), FakeBankRepository(), FakeCountryRepository()),
        deviceSecurity = deviceSecurity,
        validator = CardDraftValidator(),
        photoReader = CardPhotoReader(textScanner, barcodeScanner),
        preferencesRepository = FakeUserPreferencesRepository(),
        logoDownloader = logoDownloader,
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
    fun `leaving a new card with entered data asks before discarding it`() {
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.LOYALTY)
        viewModel.onSidesDone()
        viewModel.onCodeChange("4006381333931")

        assertTrue(viewModel.onBack()) // details -> sides
        assertTrue(viewModel.onBack()) // sides -> type
        assertTrue(viewModel.onBack()) // would close: asks instead
        assertTrue(viewModel.uiState.value.confirmDiscard)

        viewModel.onKeepEditing()
        assertFalse(viewModel.uiState.value.confirmDiscard)
        assertEquals("4006381333931", viewModel.uiState.value.loyalty.code)
    }

    @Test
    fun `editing asks before discarding changes but not when nothing changed`() = runTest {
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)
        val viewModel = viewModel(TestCards.loyalty.id.value)

        assertFalse(viewModel.onBack())

        viewModel.onTitleChange("Renamed")
        assertTrue(viewModel.onBack())
        assertTrue(viewModel.uiState.value.confirmDiscard)

        viewModel.onKeepEditing()
        viewModel.onTitleChange(TestCards.loyalty.title)
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
    fun `warns before saving a second card from the same shop`() = runTest {
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails) // custom shop "Corner Coffee"
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.LOYALTY)
        viewModel.onSidesDone()
        viewModel.onCustomShop("corner coffee")
        viewModel.onCodeChange("12345678")

        viewModel.onSave()

        assertEquals(
            DuplicateWarning(DuplicateReason.SAME_SHOP, TestCards.loyalty.title),
            viewModel.uiState.value.duplicate,
        )
        assertNull(viewModel.uiState.value.savedCardId)
        assertTrue(repository.savedDrafts.isEmpty())

        viewModel.onSave(duplicateConfirmed = true)

        assertNull(viewModel.uiState.value.duplicate)
        assertNotNull(viewModel.uiState.value.savedCardId)
    }

    @Test
    fun `warns about a card with the same name and not when editing`() = runTest {
        repository.add(TestCards.visa, TestCards.visaDetails)
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.BANK)
        viewModel.onSidesDone()
        viewModel.onNumberChange("5555555555554444")
        viewModel.onExpiryChange("0430")
        viewModel.onHolderChange("Jane Doe")
        viewModel.onTitleChange(" everyday visa ")

        viewModel.onSave()
        assertEquals(DuplicateReason.SAME_NAME, viewModel.uiState.value.duplicate?.reason)

        val editor = viewModel(TestCards.visa.id.value)
        editor.onSave()
        assertNull(editor.uiState.value.duplicate)
        assertNotNull(editor.uiState.value.savedCardId)
    }

    @Test
    fun `fills empty bank card fields from the photos but keeps typed values`() = runTest {
        textScanner.lines["content://front"] = listOf("VISA", "4111 1111 1111 1111", "VALID THRU 08/29", "JANE DOE")
        textScanner.lines["content://back"] = listOf("1111 123")
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.BANK)
        viewModel.onHolderChange("Jane M. Doe") // typed before scanning: must stay

        viewModel.onSideImagePicked(CardSide.FRONT, "content://front")
        viewModel.onSideImagePicked(CardSide.BACK, "content://back")

        val state = viewModel.uiState.value
        assertEquals("4111111111111111", state.bank.number)
        assertEquals("0829", state.bank.expiry)
        assertEquals("Jane M. Doe", state.bank.holder)
        assertTrue(state.prefilledFromPhoto)
        viewModel.onPrefillMessageShown()
        assertFalse(viewModel.uiState.value.prefilledFromPhoto)
    }

    @Test
    fun `fills an id card from its machine-readable zone`() = runTest {
        textScanner.lines["content://back"] = listOf(
            "IDD<<T220001293<<<<<<<<<<<<<<<",
            "6408125<2010315D<<<<<<<<<<<<<4",
            "MUSTERMANN<<ERIKA<<<<<<<<<<<<<",
        )
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.ID)

        viewModel.onSideImagePicked(CardSide.BACK, "content://back")

        val state = viewModel.uiState.value
        assertEquals("T22000129", state.id.documentNumber)
        assertEquals(LocalDate.of(2020, 10, 31), state.id.expiry)
        assertEquals("Erika Mustermann", state.title)
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
        assertEquals(BrandRef.Known("migros", "Migros Cumulus"), loyalty.shop)
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
    fun `choosing a shop with an official logo asks how to show it`() = runTest {
        logoDownloader.logos[MIGROS_LOGO_URL] = byteArrayOf(1, 2, 3)
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.LOYALTY)

        viewModel.onShopSelected(FakeShopRepository().shop("migros")!!)
        assertEquals("Migros Cumulus", viewModel.uiState.value.logoChoiceFor?.brandName)
        assertTrue(logoDownloader.requested.isEmpty(), "nothing is downloaded before the user decides")

        viewModel.onUseOfficialLogo()
        assertNull(viewModel.uiState.value.logoChoiceFor)
        assertIs<LogoImage.Downloaded>(viewModel.uiState.value.logo)

        viewModel.onCodeChange("4006381333931")
        viewModel.onSave()
        val logo = assertIs<ImageChange.Replace>(repository.savedDrafts.single().logo)
        assertContentEquals(byteArrayOf(1, 2, 3), (logo.source as ImageSource.Bytes).bytes)
    }

    @Test
    fun `shops without an official logo don't ask and a failed download is reported`() = runTest {
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.LOYALTY)

        viewModel.onShopSelected(FakeShopRepository().shop("lidl")!!)
        assertNull(viewModel.uiState.value.logoChoiceFor)

        viewModel.onShopSelected(FakeShopRepository().shop("migros")!!)
        viewModel.onUseOfficialLogo()
        assertTrue(viewModel.uiState.value.logoDownloadFailed)
        assertEquals(LogoImage.None, viewModel.uiState.value.logo)
    }

    @Test
    fun `custom shops can use an uploaded logo or none`() {
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.LOYALTY)
        viewModel.onCustomShop("Corner Coffee")

        viewModel.onLogoPicked("content://logo")
        assertEquals(LogoImage.Picked("content://logo"), viewModel.uiState.value.logo)

        viewModel.onRemoveLogo()
        assertEquals(LogoImage.None, viewModel.uiState.value.logo)
    }

    @Test
    fun `nfc read prefills number, expiry and holder but not the cvv`() {
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.BANK)
        viewModel.onHolderChange("Typed Name")
        viewModel.onSave()

        viewModel.onBankCardRead("4111111111111111", YearMonth.of(2030, 4), holder = null)

        val bank = viewModel.uiState.value.bank
        assertEquals("4111111111111111", bank.number)
        assertEquals("0430", bank.expiry)
        assertEquals("Typed Name", bank.holder, "a missing chip name keeps what the user typed")
        assertEquals("", bank.cvv)
        assertNull(viewModel.uiState.value.errors[CardField.NUMBER])
        assertNull(viewModel.uiState.value.errors[CardField.EXPIRY])
    }

    @Test
    fun `choosing a bank stores the issuer and offers its official logo`() = runTest {
        logoDownloader.logos[UBS_LOGO_URL] = byteArrayOf(9)
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.BANK)

        viewModel.onBankSelected(FakeBankRepository().bank("ubs")!!)
        assertEquals("UBS", viewModel.uiState.value.logoChoiceFor?.brandName)
        viewModel.onUseOfficialLogo()
        viewModel.onNumberChange("4111111111111111")
        viewModel.onExpiryChange("0430")
        viewModel.onHolderChange("Jane Doe")
        viewModel.onSave()

        val draft = repository.savedDrafts.single()
        assertEquals("UBS •••• 1111", draft.title)
        assertEquals(BrandRef.Known("ubs", "UBS"), (draft.content as CardContent.Bank).issuer)
        assertIs<ImageChange.Replace>(draft.logo)
    }

    @Test
    fun `banks without an official logo and custom banks don't prompt`() = runTest {
        val viewModel = viewModel()
        viewModel.onTypeSelected(CardType.BANK)

        viewModel.onBankSelected(FakeBankRepository().bank("neon")!!)
        assertNull(viewModel.uiState.value.logoChoiceFor)
        viewModel.onCustomBank(" My Credit Union ")
        assertEquals(BrandRef.Custom("My Credit Union"), viewModel.uiState.value.bank.issuer)
        assertNull(viewModel.uiState.value.officialLogo)
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
    fun `phone unlock alone does not open a locked card for editing`() = runTest {
        // The Keystore key stays usable for a while after unlocking the phone; that's not consent.
        repository.isAuthenticated = true
        repository.add(TestCards.idCard, TestCards.idCardDetails)

        val viewModel = viewModel(TestCards.idCard.id.value)

        assertEquals(LoadState.AUTHENTICATION_REQUIRED, viewModel.uiState.value.loadState)
        assertEquals(AuthPurpose.LOAD, viewModel.uiState.value.pendingAuthentication)
        assertEquals("", viewModel.uiState.value.id.documentNumber)
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
