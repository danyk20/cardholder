package io.github.danyk20.cardholder.feature.cardeditor

import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.testing.data.TestCards
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class DuplicateFinderTest {
    private val cards = listOf(TestCards.loyalty)

    private fun loyaltyDraft(title: String, shop: BrandRef) = CardDraft(
        id = null,
        title = title,
        color = CardColor.Default,
        content = CardContent.Loyalty(shop, BarcodeFormat.QR_CODE, CardDetails.Loyalty("1")),
    )

    @Test
    fun `a custom shop with the same name ignoring case and spaces is a duplicate`() {
        val warning = findDuplicate(cards, loyaltyDraft("Other", BrandRef.Custom("  corner coffee ")))

        assertEquals(DuplicateWarning(DuplicateReason.SAME_SHOP, TestCards.loyalty.title), warning)
    }

    @Test
    fun `the same card name is a duplicate even for another shop`() {
        val warning = findDuplicate(cards, loyaltyDraft("COFFEE CLUB", BrandRef.Custom("Bakery")))

        assertEquals(DuplicateWarning(DuplicateReason.SAME_NAME, TestCards.loyalty.title), warning)
    }

    @Test
    fun `a different shop and name is not a duplicate`() {
        assertNull(findDuplicate(cards, loyaltyDraft("Bakery card", BrandRef.Custom("Bakery"))))
        assertNull(findDuplicate(cards, loyaltyDraft("Bakery card", BrandRef.Custom(" "))))
    }
}
