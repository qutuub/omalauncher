package dev.omalauncher.personal.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

@Composable
internal fun Modifier.verticalSwipe(
    distance: Dp,
    downwards: Boolean,
    canStart: (Float, Int) -> Boolean = { _, _ -> true },
    cancelTapsOnDrag: Boolean = false,
    onSwipe: () -> Unit,
): Modifier {
    val startAllowed = rememberUpdatedState(canStart)
    val swipe = rememberUpdatedState(onSwipe)
    return pointerInput(distance, downwards, cancelTapsOnDrag) {
        val threshold = distance.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val start = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val canSwipe = startAllowed.value(start.position.y, size.height)
            if (!canSwipe && !cancelTapsOnDrag) return@awaitEachGesture
            var dragged = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size != 1) break
                val change = event.changes.single()
                if (change.id != start.id || !change.pressed) break
                val delta = change.position - start.position
                if (cancelTapsOnDrag && !dragged && delta.getDistance() > slop) {
                    dragged = true
                    // Cancel the tap and long-press detector before it sees this move.
                    change.consume()
                    if (!canSwipe) break
                }
                val vertical = if (downwards) delta.y else -delta.y
                if (vertical < -slop || abs(delta.x) > maxOf(slop, vertical)) break
                if (canSwipe && vertical >= threshold) {
                    change.consume()
                    swipe.value()
                    break
                }
            } while (true)
        }
    }
}
