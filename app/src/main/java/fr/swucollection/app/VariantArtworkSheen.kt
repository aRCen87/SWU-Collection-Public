package fr.swucollection.app

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** One short decorative reflection; never an idle loop over a collection grid. */
@Composable
internal fun rememberVariantArtworkSheen(variant: String?): Modifier {
    val context = LocalContext.current
    val finish = variant.orEmpty()
    val enabled = !LocalReducedMotion.current && ValueAnimator.areAnimatorsEnabled() &&
        (finish.contains("foil", true) || finish.contains("hyperspace", true)) &&
        context.getSharedPreferences(REFLECTION_PREFERENCES, 0).getBoolean(REFLECTION_ENABLED, true)
    val phase = remember(finish) { Animatable(-.4f) }
    LaunchedEffect(finish, enabled) {
        if (enabled) { phase.snapTo(-.4f); phase.animateTo(1.4f, tween(450)) }
        else phase.snapTo(1.4f)
    }
    if (!enabled) return Modifier
    return Modifier.drawWithContent {
        drawContent()
        val x = phase.value * size.width
        if (phase.value > -.3f && phase.value < 1.3f) drawRect(Brush.linearGradient(
            listOf(Color.Transparent, Color(0x18FFE3A0), Color(0x20D5EEFF), Color.Transparent),
            start = Offset(x - size.width * .25f, 0f), end = Offset(x + size.width * .25f, size.height)))
    }
}
