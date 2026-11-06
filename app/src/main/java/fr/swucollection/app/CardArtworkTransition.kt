package fr.swucollection.app

import android.animation.ValueAnimator
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

private data class ArtworkFrame(val card: CatalogCard, val variant: String, val reverse: Boolean, val index: Int)

/** Only the illustration transitions: collection controls always refer to the current card. */
@Composable
internal fun TransitioningCardArtwork(card: CatalogCard, variant: String, reverse: Boolean = false,
    index: Int, enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled || !ValueAnimator.areAnimatorsEnabled()) {
        CardArtwork(card, modifier, variant = variant, reverse = reverse)
        return
    }
    AnimatedContent(targetState = ArtworkFrame(card, variant, reverse, index),
        modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center,
        transitionSpec = {
            val direction = if (targetState.index >= initialState.index) 1 else -1
            val changingCard = targetState.index != initialState.index
            val enter = fadeIn(tween(200)) + slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) {
                if (changingCard) direction * it / 5 else 0
            }
            val exit = fadeOut(tween(140)) + slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) {
                if (changingCard) -direction * it / 5 else 0
            }
            (enter togetherWith exit).using(null)
        }, label = "Passage d’une carte à l’autre") { frame ->
        CardArtwork(frame.card, Modifier.fillMaxSize(), variant = frame.variant, reverse = frame.reverse)
    }
}
