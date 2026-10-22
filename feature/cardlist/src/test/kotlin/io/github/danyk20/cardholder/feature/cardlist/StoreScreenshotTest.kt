package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.country.CountrySource
import io.github.danyk20.cardholder.core.domain.country.CountryState
import io.github.danyk20.cardholder.core.domain.country.CurrentCountry
import io.github.danyk20.cardholder.core.domain.location.NearbyReason
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.testing.data.DemoCards
import io.github.danyk20.cardholder.core.ui.CardSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Play Store screenshots of the card list. Screens with card data block screen capture
 * on devices (FLAG_SECURE), so the store images are drawn here instead.
 *
 * Record with `./gradlew recordRoborazziDebug`; a normal test run only checks that the screen renders.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class StoreScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** The coffee card is suggested on top, as it would be inside the shop. */
    private val nearby = DemoCards.all
        .map { (card, details) ->
            val valid = card.withValidity()
            CardListItem(
                CardSummary(valid, valid.subtitle(), validCountries = valid.validCountries()),
                details.code(card),
            )
        }
        .partition { it.summary.card.id == DemoCards.coffee.id }

    private val state = CardListUiState.Success(
        cards = nearby.second,
        nearby = nearby.first.map { NearbyItem(it, NearbyReason.WifiHere(1.0)) },
        query = "",
        visibleTypes = CardType.entries.toSet(),
        hasAnyCards = true,
        // The country row shows the country in use and the filter switched on, as in the app.
        country = CountryChip(
            state = CountryState(
                effective = CurrentCountry(SWITZERLAND, CountrySource.NETWORK),
                detected = CurrentCountry(SWITZERLAND, CountrySource.NETWORK),
            ),
            filterEnabled = true,
        ),
        countries = listOf(
            Country(SWITZERLAND, "Switzerland"),
            Country(GERMANY, "Germany"),
            Country(AUSTRIA, "Austria"),
        ),
    )

    @Test
    fun cardListLight() = capture(darkTheme = false, name = "1_cards")

    @Test
    fun cardListDark() = capture(darkTheme = true, name = "6_cards_dark")

    private fun capture(darkTheme: Boolean, name: String) {
        composeRule.setContent {
            CardholderTheme(darkTheme = darkTheme, dynamicColor = false) {
                CardListScreen(
                    uiState = state,
                    sortActions = SortActions({}, {}, { _, _ -> }, {}, {}, {}, {}),
                    onQueryChange = {},
                    onTypeToggled = {},
                    onCardClick = {},
                    onCardLongClick = {},
                    onAddCard = {},
                    onOpenSettings = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$STORE_SCREENSHOTS/$name.png")
    }

    /** Two cards are valid in some countries only, so the store images show the validity badges. */
    private fun Card.withValidity(): Card = when (id) {
        DemoCards.grocer.id -> copy(validity = CardValidity.Countries(setOf(SWITZERLAND)))
        DemoCards.bookworm.id -> copy(validity = CardValidity.Countries(setOf(SWITZERLAND, GERMANY, AUSTRIA)))
        else -> this
    }

    private fun Card.validCountries(): List<Country> = (validity as? CardValidity.Countries)
        ?.countries
        ?.map { Country(it, COUNTRY_NAMES.getValue(it.value)) }
        .orEmpty()

    private fun Card.subtitle(): String = when (val info = info) {
        is CardInfo.Bank -> info.issuer?.name.orEmpty()
        is CardInfo.Id -> "Switzerland"
        is CardInfo.Loyalty -> info.shop.name
    }

    private fun CardDetails.code(card: Card): LoyaltyCode? {
        val format = (card.info as? CardInfo.Loyalty)?.format ?: return null
        return LoyaltyCode((this as CardDetails.Loyalty).code, format)
    }
}

private val SWITZERLAND = requireNotNull(CountryCode.of("CH"))
private val GERMANY = requireNotNull(CountryCode.of("DE"))
private val AUSTRIA = requireNotNull(CountryCode.of("AT"))
private val COUNTRY_NAMES = mapOf("CH" to "Switzerland", "DE" to "Germany", "AT" to "Austria")

/** Play Store phone screenshots, relative to the module directory tests run in. */
private const val STORE_SCREENSHOTS = "../../fastlane/metadata/android/en-US/images/phoneScreenshots"
