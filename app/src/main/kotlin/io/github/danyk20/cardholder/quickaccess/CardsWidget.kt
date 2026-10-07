package io.github.danyk20.cardholder.quickaccess

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
import io.github.danyk20.cardholder.core.ui.CardSummary
import io.github.danyk20.cardholder.core.ui.CardSummaryFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget with the loyalty cards used at the till: tap one to show its code full screen.
 * Only names and colours are shown on the home screen, never codes, and locked cards are left out.
 */
class CardsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cards = loadCards(context)
        provideContent {
            GlanceTheme { WidgetContent(context, cards) }
        }
    }

    @Suppress("TooGenericExceptionCaught") // E.g. the cards can't be decrypted: show the empty widget.
    private suspend fun loadCards(context: Context): List<CardSummary> = try {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val cards = quickAccessCards(entryPoint.cardRepository().observeCards().first(), MAX_CARDS)
        entryPoint.summaryFactory().summarize(cards)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun cardRepository(): CardRepository

        fun summaryFactory(): CardSummaryFactory
    }

    private companion object {
        const val MAX_CARDS = 8
    }
}

class CardsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardsWidget()
}

@Composable
private fun WidgetContent(context: Context, cards: List<CardSummary>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(10.dp),
    ) {
        Text(
            context.getString(R.string.widget_title),
            style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.padding(start = 6.dp, bottom = 8.dp),
        )
        if (cards.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    context.getString(R.string.widget_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.padding(8.dp),
                )
            }
        } else {
            LazyColumn {
                items(cards, itemId = { it.card.id.value.hashCode().toLong() }) { summary ->
                    Column {
                        CardRow(context, summary)
                        Spacer(GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CardRow(context: Context, summary: CardSummary) {
    val colors = summary.faceColors
    Text(
        summary.card.title,
        maxLines = 1,
        style = TextStyle(color = ColorProvider(colors.content), fontWeight = FontWeight.Medium),
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(12.dp)
            .background(colors.start)
            .clickable(actionStartActivity(showCodeIntent(context, summary.card)))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    )
}
