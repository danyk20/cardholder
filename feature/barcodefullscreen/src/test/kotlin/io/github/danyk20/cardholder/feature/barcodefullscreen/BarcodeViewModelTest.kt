package io.github.danyk20.cardholder.feature.barcodefullscreen

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.testing.MainDispatcherRule
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeSessionLockEvents
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarcodeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeCardRepository()
    private val sessionLock = FakeSessionLockEvents()

    private fun viewModel(card: Card) = BarcodeViewModel(
        savedStateHandle = SavedStateHandle(mapOf("cardId" to card.id.value)),
        cardRepository = repository,
        sessionLockEvents = sessionLock,
    )

    @Test
    fun `shows the code of an unlocked loyalty card`() {
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)

        assertEquals(
            BarcodeUiState.Ready(
                title = "Coffee club",
                shopName = "Corner Coffee",
                code = "4006381333931",
                format = BarcodeFormat.EAN_13,
                isLocked = false,
            ),
            viewModel(TestCards.loyalty).uiState.value,
        )
    }

    @Test
    fun `locked card needs authentication and re-locks in the background`() {
        val locked = TestCards.loyalty.copy(isLocked = true)
        repository.add(locked, TestCards.loyaltyDetails)
        val viewModel = viewModel(locked)
        assertEquals(BarcodeUiState.AuthenticationRequired(fromLock = true), viewModel.uiState.value)

        repository.isAuthenticated = true
        viewModel.onAuthenticationResult(succeeded = true)
        assertEquals("4006381333931", (viewModel.uiState.value as BarcodeUiState.Ready).code)

        sessionLock.lock()
        assertEquals(BarcodeUiState.AuthenticationRequired(fromLock = true), viewModel.uiState.value)
    }

    @Test
    fun `phone unlock alone does not show a locked card's code`() {
        // The Keystore key stays usable for a while after unlocking the phone; that's not consent.
        repository.isAuthenticated = true
        val locked = TestCards.loyalty.copy(isLocked = true)
        repository.add(locked, TestCards.loyaltyDetails)

        assertEquals(BarcodeUiState.AuthenticationRequired(fromLock = true), viewModel(locked).uiState.value)
    }

    @Test
    fun `after the app was in the background a locked code needs a new prompt`() {
        val locked = TestCards.loyalty.copy(isLocked = true)
        repository.add(locked, TestCards.loyaltyDetails)
        repository.isAuthenticated = true
        val viewModel = viewModel(locked)
        viewModel.onAuthenticationResult(succeeded = true)
        sessionLock.lock()

        // A database change (e.g. an edit elsewhere) must not bring the code back without a prompt.
        repository.add(locked.copy(updatedAt = locked.updatedAt.plusSeconds(1)), TestCards.loyaltyDetails)

        assertEquals(BarcodeUiState.AuthenticationRequired(fromLock = true), viewModel.uiState.value)
    }

    @Test
    fun `cancelled authentication does not prompt again automatically`() {
        val locked = TestCards.loyalty.copy(isLocked = true)
        repository.add(locked, TestCards.loyaltyDetails)
        val viewModel = viewModel(locked)

        viewModel.onAuthenticationResult(succeeded = false)

        assertEquals(BarcodeUiState.AuthenticationRequired(fromLock = false), viewModel.uiState.value)
    }

    @Test
    fun `shows the new code after the card was edited`() {
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)
        val viewModel = viewModel(TestCards.loyalty)

        val edited = TestCards.loyalty.copy(
            title = "Coffee club gold",
            info = CardInfo.Loyalty(BrandRef.Custom("Corner Coffee"), BarcodeFormat.QR_CODE),
            updatedAt = TestCards.loyalty.updatedAt.plusSeconds(60),
        )
        repository.add(edited, CardDetails.Loyalty(code = "GOLD-42"))

        val state = viewModel.uiState.value as BarcodeUiState.Ready
        assertEquals("GOLD-42", state.code)
        assertEquals(BarcodeFormat.QR_CODE, state.format)
        assertEquals("Coffee club gold", state.title)
    }

    @Test
    fun `closes when the card is deleted while its code is shown`() = runTest {
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)
        val viewModel = viewModel(TestCards.loyalty)

        repository.delete(TestCards.loyalty.id)

        assertEquals(BarcodeUiState.Removed, viewModel.uiState.value)
    }

    @Test
    fun `damaged card data shows an error instead of crashing`() {
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)
        repository.damaged += TestCards.loyalty.id

        assertEquals(BarcodeUiState.Failed, viewModel(TestCards.loyalty).uiState.value)
    }

    @Test
    fun `non-loyalty cards are not found`() {
        repository.add(TestCards.visa, TestCards.visaDetails)

        assertEquals(BarcodeUiState.NotFound, viewModel(TestCards.visa).uiState.value)
    }
}
