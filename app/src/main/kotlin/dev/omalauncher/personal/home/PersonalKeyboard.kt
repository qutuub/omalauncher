package dev.omalauncher.personal.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val keyFill = Color(0xff11131d)
private val keyBorder = Color(0xff23283d)
private val keyPressedFill = Color(0xff29324e)
private val keyAccent = Color(0xff7aa2f7)
private val keyShape = RoundedCornerShape(6.dp)
private const val KEY_REPEAT_DELAY = 400L
private const val KEY_REPEAT_INTERVAL = 50L

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonalKeyboard(
    state: KeyboardState,
    onKey: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = keyRows(state.layer, state.shift)
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom)),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            KeyRow(codingKeys, state, onKey, height = 36.dp, textSize = 12)
            rows.forEachIndexed { rowIndex, row ->
                val sideInset = if (state.layer == KeyboardLayer.Letters && rowIndex == 1) 14.dp else 0.dp
                KeyRow(
                    row,
                    state,
                    onKey,
                    height = 46.dp,
                    textSize = 16,
                    modifier = Modifier.padding(horizontal = sideInset),
                )
            }
        }
    }
}

@Composable
private fun KeyRow(
    keys: List<KeySpec>,
    state: KeyboardState,
    onKey: (KeyAction) -> Unit,
    height: Dp,
    textSize: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { spec ->
            val latched = when (spec.action) {
                KeyAction.Ctrl -> state.ctrl
                KeyAction.Shift -> state.shift != ShiftMode.Off
                else -> false
            }
            val locked = spec.action == KeyAction.Shift && state.shift == ShiftMode.Locked
            KeyButton(
                spec = spec,
                latched = latched,
                locked = locked,
                height = height,
                textSize = textSize,
                onPress = { onKey(spec.action) },
                modifier = Modifier.weight(spec.weight),
            )
        }
    }
}

@Composable
private fun KeyButton(
    spec: KeySpec,
    latched: Boolean,
    locked: Boolean,
    height: Dp,
    textSize: Int,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val currentPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }

    val fill = when {
        pressed -> keyPressedFill
        locked -> keyAccent
        else -> keyFill
    }
    val outline = when {
        pressed || latched || spec.accent -> keyAccent
        else -> keyBorder
    }
    val textColor = when {
        locked && !pressed -> Color.Black
        latched || spec.accent -> keyAccent
        spec.action is KeyAction.Text -> foreground
        else -> muted
    }
    val fontSize = if (spec.label.length > 1) minOf(textSize, 13) else textSize

    Box(
        modifier
            .height(height)
            .clip(keyShape)
            .background(fill, keyShape)
            .border(1.dp, outline, keyShape)
            .semantics {
                role = Role.Button
                contentDescription = spec.label
            }
            .pointerInput(spec.action, spec.repeats) {
                coroutineScope {
                    val scope = this
                    awaitEachGesture {
                        awaitFirstDown()
                        pressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val job = scope.launch {
                            currentPress()
                            if (!spec.repeats) return@launch
                            delay(KEY_REPEAT_DELAY)
                            while (true) {
                                currentPress()
                                delay(KEY_REPEAT_INTERVAL)
                            }
                        }
                        waitForUpOrCancellation()
                        job.cancel()
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            spec.label,
            style = TextStyle(color = textColor, fontFamily = FontFamily.Monospace, fontSize = fontSize.sp),
            maxLines = 1,
        )
    }
}
