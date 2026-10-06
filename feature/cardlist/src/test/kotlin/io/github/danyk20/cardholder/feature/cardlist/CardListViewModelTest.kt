package io.github.danyk20.cardholder.feature.cardlist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.danyk20.cardholder.core.domain.usecase.ObserveCardsUseCase
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardSort
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.testing.MainDispatcherRule
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeBankRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeCountryRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeShopRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeUserPreferencesRepository
import io.github.danyk20.cardholder.core.ui.CardSummaryFactory
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CardListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeCardRepository()
    private val preferences = FakeUserPreferencesRepository()
    private val viewModel by lazy {
        CardListViewModel(
            observeCards = ObserveCardsUseCase(repository),
            cardRepository = repository,
            summaryFactory = CardSummaryFactory(FakeShopRepository(), FakeBankRepository(), FakeCountryRepository()),
            preferencesRepository = preferences,
            savedStateHandle = SavedStateHandle(),
        )
    }

    @Test
    fun `shows empty state when there are no cards`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem().let { if (it is CardListUiState.Loading) awaitItem() else it }
            assertIs<CardListUiState.Success>(state)
            assertEquals(false, state.hasAnyCards)
        }
    }

    @Test
    fun `lists cards with resolved subtitles`() = runTest {
        repository.add(TestCards.visa, TestCards.visaDetails)
        repository.add(TestCards.idCard, TestCards.idCardDetails)

        viewModel.uiState.test {
            val state = awaitSuccess()
            assertEquals(listOf("Everyday Visa", "Swiss ID"), state.cards.map { it.summary.card.title })
            assertEquals("Visa", state.cards[0].summary.subtitle)
            assertEquals("🇨🇭 Switzerland", state.cards[1].summary.subtitle)
        }
    }

    @Test
    fun `a damaged loyalty card doesn't break the list`() = runTest {
        repository.add(TestCards.visa, TestCards.visaDetails)
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)
        repository.damaged += TestCards.loyalty.id

        viewModel.uiState.test {
            val state = awaitSuccess()
            assertEquals(2, state.cards.size)
            assertEquals(null, state.cards.single { it.summary.card.id == TestCards.loyalty.id }.code)
        }
    }

    @Test
    fun `filters and searches`() = runTest {
        repository.add(TestCards.visa, TestCards.visaDetails)
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)

        viewModel.uiState.test {
            awaitSuccess()
            viewModel.onTypeToggled(CardType.BANK)
            assertEquals(listOf(TestCards.loyalty.id), awaitSuccess().cards.map { it.summary.card.id })

            viewModel.onTypeToggled(CardType.BANK)
            assertEquals(CardType.entries.toSet(), awaitSuccess().visibleTypes)
            viewModel.onQueryChange("visa")
            val searched = awaitSuccess()
            assertEquals(listOf(TestCards.visa.id), searched.cards.map { it.summary.card.id })
            assertEquals(true, searched.hasAnyCards)
        }
    }

    @Test
    fun `unlocked loyalty cards carry their code, locked ones don't`() = runTest {
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)
        repository.add(
            TestCards.loyalty.copy(id = CardId("locked"), title = "Locked club", isLocked = true),
            TestCards.loyaltyDetails,
        )
        repository.add(TestCards.visa, TestCards.visaDetails)

        viewModel.uiState.test {
            val codes = awaitSuccess().cards.associate { it.summary.card.title to it.code }
            assertEquals(LoyaltyCode("4006381333931", BarcodeFormat.EAN_13), codes["Coffee club"])
            assertEquals(null, codes["Locked club"])
            assertEquals(null, codes["Everyday Visa"])
        }
    }

    @Test
    fun `sort choice is applied and remembered`() = runTest {
        repository.add(TestCards.visa.copy(createdAt = Instant.parse("2026-01-01T00:00:00Z")), TestCards.visaDetails)
        repository.add(
            TestCards.loyalty.copy(createdAt = Instant.parse("2026-06-01T00:00:00Z")),
            TestCards.loyaltyDetails,
        )

        viewModel.uiState.test {
            assertEquals(listOf("Coffee club", "Everyday Visa"), awaitSuccess().cards.map { it.summary.card.title })
            viewModel.onSortChange(CardSort.DATE_ADDED)
            val sorted = awaitSuccess()
            assertEquals(CardSort.DATE_ADDED, sorted.sort)
            assertEquals(CardSort.DATE_ADDED, preferences.preferences.value.cardSort)
        }
    }

    @Test
    fun `reordering moves cards and saves the custom order`() = runTest {
        repository.add(TestCards.visa, TestCards.visaDetails)
        repository.add(TestCards.idCard, TestCards.idCardDetails)
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)

        viewModel.uiState.test {
            awaitSuccess()
            viewModel.onStartReorder()
            var state = awaitSuccess()
            while (state.reordering == null) state = awaitSuccess()
            assertEquals(CardSort.CUSTOM, preferences.preferences.value.cardSort)
            assertEquals(
                listOf(TestCards.visa.id, TestCards.idCard.id, TestCards.loyalty.id),
                state.reordering!!.map {
                    it.card.id
                },
            )

            viewModel.onMove(from = 2, to = 0)
            viewModel.onMoveBy(TestCards.visa.id, offset = 1)
            assertEquals(
                listOf(TestCards.loyalty.id, TestCards.idCard.id, TestCards.visa.id),
                expectMostRecentItem().let { it as CardListUiState.Success }.reordering!!.map { it.card.id },
            )

            viewModel.onReorderDone()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(
            listOf(TestCards.loyalty.id, TestCards.idCard.id, TestCards.visa.id),
            repository.observeCards().first().sortedBy { it.position }.map { it.id },
        )
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<CardListUiState>.awaitSuccess(): CardListUiState.Success {
        var item = awaitItem()
        while (item !is CardListUiState.Success) item = awaitItem()
        return item
    }
}
