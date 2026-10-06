package io.github.danyk20.cardholder.feature.carddetail

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.testing.MainDispatcherRule
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeCountryRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeDeviceSecurity
import io.github.danyk20.cardholder.core.testing.repository.FakeSecureClipboard
import io.github.danyk20.cardholder.core.testing.repository.FakeSessionLockEvents
import io.github.danyk20.cardholder.core.testing.repository.FakeShopRepository
import io.github.danyk20.cardholder.core.ui.CardSummaryFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeCardRepository()
    private val clipboard = FakeSecureClipboard()
    private val sessionLock = FakeSessionLockEvents()

    private fun viewModel(card: Card) = CardDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("cardId" to card.id.value)),
        cardRepository = repository,
        summaryFactory = CardSummaryFactory(FakeShopRepository(), FakeCountryRepository()),
        deviceSecurity = FakeDeviceSecurity(),
        clipboard = clipboard,
        sessionLockEvents = sessionLock,
    )

    @Test
    fun `unlocked card shows details but hides the cvv`() {
        repository.add(TestCards.visa, TestCards.visaDetails, TestCards.VISA_CVV)

        val state = viewModel(TestCards.visa).uiState.value

        assertEquals(DetailsState.Loaded(TestCards.visaDetails), state.details)
        assertNull(state.cvv)
        assertNull(state.pendingAuthentication)
    }

    @Test
    fun `revealing the cvv requires authentication`() {
        repository.add(TestCards.visa, TestCards.visaDetails, TestCards.VISA_CVV)
        val viewModel = viewModel(TestCards.visa)

        viewModel.onRevealCvv()
        assertEquals(AuthAction.REVEAL_CVV, viewModel.uiState.value.pendingAuthentication)

        repository.isAuthenticated = true
        viewModel.onAuthenticationResult(AuthAction.REVEAL_CVV, succeeded = true)
        assertEquals(TestCards.VISA_CVV, viewModel.uiState.value.cvv)

        viewModel.onHideCvv()
        assertNull(viewModel.uiState.value.cvv)
    }

    @Test
    fun `locked card prompts once and reveals everything after unlocking`() {
        val lockedVisa = TestCards.visa.copy(isLocked = true)
        repository.add(lockedVisa, TestCards.visaDetails, TestCards.VISA_CVV)
        val viewModel = viewModel(lockedVisa)

        assertEquals(DetailsState.Locked, viewModel.uiState.value.details)
        assertEquals(AuthAction.UNLOCK_DETAILS, viewModel.uiState.value.pendingAuthentication)

        repository.isAuthenticated = true
        viewModel.onAuthenticationResult(AuthAction.UNLOCK_DETAILS, succeeded = true)

        assertEquals(DetailsState.Loaded(TestCards.visaDetails), viewModel.uiState.value.details)
        assertEquals(TestCards.VISA_CVV, viewModel.uiState.value.cvv)
    }

    @Test
    fun `going to the background hides decrypted data of locked cards`() {
        val lockedVisa = TestCards.visa.copy(isLocked = true)
        repository.add(lockedVisa, TestCards.visaDetails, TestCards.VISA_CVV)
        repository.isAuthenticated = true
        val viewModel = viewModel(lockedVisa)
        viewModel.onRevealCvv()

        sessionLock.lock()

        assertEquals(DetailsState.Locked, viewModel.uiState.value.details)
        assertNull(viewModel.uiState.value.cvv)
    }

    @Test
    fun `removing the lock asks for authentication`() = runTest {
        repository.add(TestCards.idCard, TestCards.idCardDetails)
        val viewModel = viewModel(TestCards.idCard)
        viewModel.onAuthenticationResult(AuthAction.UNLOCK_DETAILS, succeeded = false)

        viewModel.onLockedChange(false)
        assertEquals(AuthAction.REMOVE_LOCK, viewModel.uiState.value.pendingAuthentication)

        repository.isAuthenticated = true
        viewModel.onAuthenticationResult(AuthAction.REMOVE_LOCK, succeeded = true)
        assertFalse(repository.observeCard(TestCards.idCard.id).first()!!.isLocked)
    }

    @Test
    fun `copy uses the secure clipboard`() {
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)
        val viewModel = viewModel(TestCards.loyalty)

        viewModel.onCopy("Code", TestCards.loyaltyDetails.code)

        assertEquals(listOf("Code" to TestCards.loyaltyDetails.code), clipboard.copied)
        assertEquals("Code", viewModel.uiState.value.copiedLabel)
    }

    @Test
    fun `delete removes the card`() = runTest {
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)
        val viewModel = viewModel(TestCards.loyalty)

        viewModel.onDelete()

        assertTrue(viewModel.uiState.value.isDeleted)
        assertNull(repository.observeCard(TestCards.loyalty.id).first())
    }
}
