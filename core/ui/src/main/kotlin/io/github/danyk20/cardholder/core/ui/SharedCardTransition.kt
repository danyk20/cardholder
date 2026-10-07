package io.github.danyk20.cardholder.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import io.github.danyk20.cardholder.core.model.CardId

/** Provided around the app's navigation so cards can move between screens. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The enter/exit animation of the current navigation destination. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Makes [scope] (a navigation destination's animation) available to [sharedCardBounds] inside [content]. */
@Composable
fun ProvideNavAnimatedVisibilityScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides scope, content = content)

/**
 * The same card on two screens (list and details) morphs from one to the other while navigating.
 * Does nothing outside a shared transition, e.g. in previews and screenshot tests.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCardBounds(cardId: CardId): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@sharedCardBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "card-${cardId.value}"),
            animatedVisibilityScope = visibilityScope,
        )
    }
}
