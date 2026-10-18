package io.github.danyk20.cardholder.quickaccess

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.danyk20.cardholder.R
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.ui.CardSummary
import io.github.danyk20.cardholder.core.ui.CardSummaryFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget with the loyalty cards used at the till: tap one to show its code full screen.
 * Only names and colours are shown on the home screen, never codes, and locked cards are left out.
 * The refresh button sorts the cards by location (see [NearbyRefreshActivity]); cards found nearby
 * stay on top, marked with a pin, until the next refresh. From 2x1 cells it shows the top card only.
 */
class CardsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = loadCards(context)
        provideContent {
            GlanceTheme { WidgetContent(context, content) }
        }
    }

    @Suppress("TooGenericExceptionCaught") // E.g. the cards can't be decrypted: show the empty widget.
    private suspend fun loadCards(context: Context): WidgetCards = try {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val nearby = entryPoint.nearbyOrder().cardIds()
        val cards = quickAccessCards(entryPoint.cardRepository().observeCards().first(), MAX_CARDS, nearby)
        WidgetCards(entryPoint.summaryFactory().summarize(cards), nearby.toSet())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        WidgetCards(emptyList(), emptySet())
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun cardRepository(): CardRepository

        fun summaryFactory(): CardSummaryFactory

        fun nearbyOrder(): WidgetNearbyOrder
    }

    private companion object {
        const val MAX_CARDS = 8
    }
}

class CardsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardsWidget()
}

/** The cards to show, and which of them the last "sort by location" found nearby. */
private class WidgetCards(val cards: List<CardSummary>, val nearby: Set<CardId>)

/**
 * The height the full layout needs: padding (2 x 10), the header (40 + 4) and one card row (48).
 * Below it (one cell on most launchers) the widget shows only the top card and the refresh button.
 */
private val CompactHeight = 112.dp

@Composable
private fun WidgetContent(context: Context, content: WidgetCards) {
    val compact = LocalSize.current.height < CompactHeight
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(if (compact) 6.dp else 10.dp),
    ) {
        if (compact) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.defaultWeight()) {
                    val top = content.cards.firstOrNull()
                    if (top ==
                        null
                    ) {
                        EmptyText(context)
                    } else {
                        CardRow(context, top, top.card.id in content.nearby, compact = true)
                    }
                }
                RefreshButton(context)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp),
            ) {
                Text(
                    context.getString(R.string.widget_title),
                    style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                    modifier = GlanceModifier.defaultWeight(),
                )
                RefreshButton(context)
            }
            if (content.cards.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyText(context) }
            } else {
                LazyColumn {
                    items(content.cards, itemId = { it.card.id.value.hashCode().toLong() }) { summary ->
                        Column {
                            CardRow(context, summary, summary.card.id in content.nearby)
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyText(context: Context) {
    Text(
        context.getString(R.string.widget_empty),
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
        modifier = GlanceModifier.padding(8.dp),
    )
}

/** Sorts the cards by where the user is; opens Settings instead while that's switched off. */
@Composable
private fun RefreshButton(context: Context) {
    Image(
        provider = ImageProvider(R.drawable.ic_widget_refresh),
        contentDescription = context.getString(R.string.widget_sort_by_location),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
        modifier = GlanceModifier
            .size(40.dp)
            .cornerRadius(20.dp)
            .clickable(
                actionStartActivity(
                    Intent(context, NearbyRefreshActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ),
            )
            .padding(8.dp),
    )
}

@Composable
private fun CardRow(context: Context, summary: CardSummary, isNearby: Boolean, compact: Boolean = false) {
    val colors = summary.faceColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(12.dp)
            .background(colors.start)
            .clickable(actionStartActivity(showCodeIntent(context, summary.card)))
            .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 14.dp),
    ) {
        Text(
            summary.card.title,
            maxLines = 1,
            style = TextStyle(color = ColorProvider(colors.content), fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (isNearby) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_nearby),
                contentDescription = context.getString(R.string.widget_nearby),
                colorFilter = ColorFilter.tint(ColorProvider(colors.content)),
                modifier = GlanceModifier.size(16.dp),
            )
        }
    }
}
