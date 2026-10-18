package io.github.danyk20.cardholder.core.domain.location

import io.github.danyk20.cardholder.core.model.Card
import java.time.Duration
import java.time.Instant

/** A card whose place the user just arrived at. */
data class ArrivalCandidate(
    val card: Card,
    val place: Place,
    /** When the user was last notified about this card at any of its places. */
    val lastNotifiedAt: Instant?,
)

/** Keeps arrival notifications rare enough to stay useful. */
object ArrivalPolicy {
    /** A card used this recently was already at hand. */
    val RecentUse: Duration = Duration.ofHours(2)

    /** The same card isn't suggested again sooner. */
    val CardCooldown: Duration = Duration.ofHours(4)

    /** No notification follows another one sooner, so a shopping centre doesn't cause a burst. */
    val AnyCooldown: Duration = Duration.ofMinutes(30)

    /**
     * The one card to notify about, the one used most at its place, or `null` when it's too soon
     * after the [lastNotification] about any card.
     */
    fun choose(candidates: List<ArrivalCandidate>, lastNotification: Instant?, now: Instant): ArrivalCandidate? {
        if (lastNotification != null && Duration.between(lastNotification, now) < AnyCooldown) return null
        return candidates
            .filter { it.card.suggestByLocation }
            .filterNot { it.card.lastUsedAt.isWithin(RecentUse, now) || it.lastNotifiedAt.isWithin(CardCooldown, now) }
            .maxWithOrNull(compareBy<ArrivalCandidate> { it.place.useCount }.thenBy { it.place.lastUsedAt })
    }

    private fun Instant?.isWithin(duration: Duration, now: Instant): Boolean =
        this != null && Duration.between(this, now) < duration
}
