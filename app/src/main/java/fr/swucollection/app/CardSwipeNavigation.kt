package fr.swucollection.app

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Single horizontal swipes navigate; multi-touch remains available to image zoom. */
internal fun Modifier.cardSwipeNavigation(identity: Any, enabled: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit): Modifier = pointerInput(identity, enabled) {
    if (enabled) awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var x = 0f; var y = 0f; var multi = false
        val threshold = 64.dp.toPx()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } > 1) multi = true
            if (!multi) event.changes.firstOrNull { it.id == first.id }?.let {
                x += it.positionChange().x; y += it.positionChange().y
                if (abs(x) > threshold && abs(x) > abs(y)) it.consume()
            }
        } while (event.changes.any { it.pressed })
        if (!multi && abs(x) > threshold && abs(x) > abs(y)) {
            if (x < 0) onNext() else onPrevious()
        }
    }
}
