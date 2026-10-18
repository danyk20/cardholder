package io.github.danyk20.cardholder.core.domain.location

import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.testing.data.TestCards
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class ArrivalPolicyTest {
    @Test
    fun `notifies about the card used most at its place`() {
        val candidates = listOf(candidate("rare", useCount = 2), candidate("often", useCount = 9))

        assertEquals("often", ArrivalPolicy.choose(candidates, null, Now)?.card?.id?.value)
    }

    @Test
    fun `waits half an hour after any notification`() {
        val candidates = listOf(candidate("card"))

        assertNull(ArrivalPolicy.choose(candidates, Now.minus(Duration.ofMinutes(29)), Now))
        assertEquals(candidates.first(), ArrivalPolicy.choose(candidates, Now.minus(Duration.ofMinutes(31)), Now))
    }

    @Test
    fun `skips cards used or suggested recently`() {
        assertNull(ArrivalPolicy.choose(listOf(candidate("card", lastUsed = Duration.ofHours(1))), null, Now))
        assertNull(ArrivalPolicy.choose(listOf(candidate("card", lastNotified = Duration.ofHours(3))), null, Now))
        assertEquals(
            "card",
            ArrivalPolicy.choose(
                listOf(candidate("card", lastUsed = Duration.ofHours(3), lastNotified = Duration.ofHours(5))),
                null,
                Now,
            )?.card?.id?.value,
        )
    }

    @Test
    fun `skips cards with suggestions switched off`() {
        val candidate = candidate("card").let { it.copy(card = it.card.copy(suggestByLocation = false)) }

        assertNull(ArrivalPolicy.choose(listOf(candidate), null, Now))
    }

    private fun candidate(id: String, useCount: Int = 1, lastUsed: Duration? = null, lastNotified: Duration? = null) =
        ArrivalCandidate(
            card = TestCards.loyalty.copy(
                id = CardId(id),
                suggestByLocation = true,
                lastUsedAt = lastUsed?.let(Now::minus),
            ),
            place = place(id.hashCode().toLong(), id, useCount = useCount),
            lastNotifiedAt = lastNotified?.let(Now::minus),
        )
}
