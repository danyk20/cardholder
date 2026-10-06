package io.github.danyk20.cardholder.feature.cardlist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.danyk20.cardholder.core.domain.usecase.ObserveCardsUseCase
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.testing.MainDispatcherRule
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeCountryRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeShopRepository
import io.github.danyk20.cardholder.core.ui.CardSummaryFactory
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CardListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeCardRepository()
    private val viewModel by lazy {
        CardListViewModel(
            observeCards = ObserveCardsUseCase(repository),
            summaryFactory = CardSummaryFactory(FakeShopRepository(), FakeCountryRepository()),
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
            assertEquals(listOf("Everyday Visa", "Swiss ID"), state.cards.map { it.card.title })
            assertEquals("Visa", state.cards[0].subtitle)
            assertEquals("🇨🇭 Switzerland", state.cards[1].subtitle)
        }
    }

    @Test
    fun `filters and searches`() = runTest {
        repository.add(TestCards.visa, TestCards.visaDetails)
        repository.add(TestCards.loyalty, TestCards.loyaltyDetails)

        viewModel.uiState.test {
            awaitSuccess()
            viewModel.onFilterChange(CardType.LOYALTY)
            assertEquals(listOf(TestCards.loyalty.id), awaitSuccess().cards.map { it.card.id })

            viewModel.onFilterChange(null)
            awaitSuccess()
            viewModel.onQueryChange("visa")
            val searched = awaitSuccess()
            assertEquals(listOf(TestCards.visa.id), searched.cards.map { it.card.id })
            assertEquals(true, searched.hasAnyCards)
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<CardListUiState>.awaitSuccess(): CardListUiState.Success {
        var item = awaitItem()
        while (item !is CardListUiState.Success) item = awaitItem()
        return item
    }
}
