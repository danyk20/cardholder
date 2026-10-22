package io.github.danyk20.cardholder.quickaccess

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.danyk20.cardholder.MainActivity
import io.github.danyk20.cardholder.R
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.location.isUsable
import io.github.danyk20.cardholder.core.domain.repository.CurrentCountryRepository
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.domain.usecase.FindNearbyCardsUseCase
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.PlaceSuggestions
import io.github.danyk20.cardholder.feature.settings.navigation.SETTINGS_DEEP_LINK
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The widget's "sort by location": a small popup over the home screen that looks up the cards used
 * where the user is (the same lookup as the card list), re-sorts the widget and closes. Android only lets
 * apps read the location while they're visible, hence a screen rather than work in the background.
 * If card suggestions are off or the location can't be used, it opens Settings instead.
 */
@AndroidEntryPoint
class NearbyRefreshActivity : ComponentActivity() {
    @Inject
    lateinit var findNearbyCards: FindNearbyCardsUseCase

    @Inject
    lateinit var preferences: UserPreferencesRepository

    @Inject
    lateinit var locationAccess: LocationAccess

    @Inject
    lateinit var nearbyOrder: WidgetNearbyOrder

    @Inject
    lateinit var currentCountry: CurrentCountryRepository

    private var foundNothing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CardholderTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RefreshPopup(foundNothing)
                }
            }
        }
        lifecycleScope.launch { refresh() }
    }

    private suspend fun refresh() {
        // The widget shows the cards valid in the current country, so that is detected again as well.
        currentCountry.refresh()
        val enabled = preferences.preferences.first().placeSuggestions != PlaceSuggestions.OFF
        if (!enabled || !locationAccess.isUsable) {
            openSettings()
            return
        }
        // The list may have looked a minute ago, somewhere else: read the surroundings again.
        findNearbyCards.invalidate()
        val nearby = withTimeoutOrNull(LOOKUP_TIMEOUT_MILLIS) { findNearbyCards(setOf(CardType.LOYALTY)) }.orEmpty()
        nearbyOrder.set(nearby.map { it.cardId })
        CardsWidget().updateAll(applicationContext)
        if (nearby.isEmpty()) {
            foundNothing = true
            delay(NOTHING_FOUND_MILLIS)
        }
        finish()
    }

    private fun openSettings() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(SETTINGS_DEEP_LINK.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }

    private companion object {
        const val LOOKUP_TIMEOUT_MILLIS = 15_000L
        const val NOTHING_FOUND_MILLIS = 1_500L
    }
}

@Composable
private fun RefreshPopup(foundNothing: Boolean) {
    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp, shadowElevation = 6.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            if (!foundNothing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(
                stringResource(if (foundNothing) R.string.widget_nothing_nearby else R.string.widget_finding_nearby),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
