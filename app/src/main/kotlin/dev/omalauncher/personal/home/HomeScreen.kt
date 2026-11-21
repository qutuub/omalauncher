package dev.omalauncher.personal.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.omalauncher.R
import java.time.LocalDate

internal val foreground = Color(0xffc0caf5)
internal val muted = Color(0xff8089b3)
private val dim = Color(0xff565f89)
private val consoleAccent = Color(0xff7aa2f7)
private val warning = Color(0xffe0af68)
internal val hairline = Color(0xff1f2335)
private val promptSurface = Color(0xff0b0d14)
private val promptShape = RoundedCornerShape(4.dp)

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HomeScreen(
    status: HomeStatus?,
    vault: VaultSnapshot,
    today: LocalDate,
    launchFailure: String?,
    onCompleteTask: (VaultTask) -> Unit,
    openUpstream: () -> Unit,
    openSearch: () -> Unit,
) {
    val openLabel = stringResource(R.string.personal_open_upstream)
    val safeInsets = WindowInsets.displayCutout.union(WindowInsets.systemGestures)
        .union(WindowInsets.navigationBarsIgnoringVisibility)
    val density = LocalDensity.current
    val topEdge = safeInsets.union(WindowInsets.statusBarsIgnoringVisibility).getTop(density)
    val bottomEdge = safeInsets.getBottom(density)
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .verticalSwipe(
                60.dp,
                downwards = true,
                canStart = { y, height -> y >= topEdge && y < height - bottomEdge },
                cancelTapsOnDrag = true,
                onSwipe = openSearch,
            )
            .pointerInput(openUpstream) { detectTapGestures(onLongPress = { openUpstream() }) }
            .semantics { onLongClick(openLabel) { openUpstream(); true } },
    ) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(safeInsets)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            if (status != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    MonoText(status.clockSeconds, foreground, 22)
                    MonoText(status.isoDate, muted, 12)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MonoText(status.machine, dim, 11, Modifier.weight(1f))
                    val rightMeta = listOfNotNull(status.uptime, status.alarm).joinToString(" · ")
                    if (rightMeta.isNotEmpty()) {
                        MonoText(rightMeta, dim, 11)
                    }
                }
                Hairline(Modifier.padding(vertical = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    status.telemetry.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MonoText(row.tag, consoleAccent, 12, Modifier.width(30.dp))
                            MonoText(row.value, if (row.warn) warning else foreground, 12, Modifier.weight(1f))
                        }
                    }
                }
                Hairline(Modifier.padding(top = 8.dp))
            }

            if (launchFailure != null) {
                MonoText(launchFailure, warning, 12, Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.weight(1f))

            if (vault.exists || vault.openTasks.isNotEmpty()) {
                val taskTag = "TSK"
                val taskFile = "todo.md"
                val openCount = "${vault.openTasks.size}"
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        MonoText(taskTag, consoleAccent, 12, Modifier.width(30.dp))
                        MonoText(openCount, muted, 12)
                    }
                    MonoText(taskFile, dim, 11)
                }
                Hairline(Modifier.padding(top = 6.dp, bottom = 4.dp))
                if (vault.openTasks.isEmpty()) {
                    val emptyMark = "[x] --"
                    MonoText(emptyMark, dim, 12, Modifier.padding(vertical = 6.dp))
                } else {
                    Column {
                        vault.openTasks.take(5).forEach { task ->
                            TaskRow(task, today) { onCompleteTask(task) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(promptSurface, promptShape)
                    .border(1.dp, hairline, promptShape)
                    .clickable(role = Role.Button, onClick = openSearch)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoText(stringResource(R.string.personal_search_prompt), consoleAccent, 16)
                MonoText(stringResource(R.string.personal_search_placeholder), dim, 13, Modifier.weight(1f))
                val enterGlyph = "↵"
                MonoText(enterGlyph, dim, 13)
            }
        }
    }
}

@Composable
private fun TaskRow(task: VaultTask, today: LocalDate, onComplete: () -> Unit) {
    val boxGlyph = "[ ]"
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClick = onComplete)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(boxGlyph, consoleAccent, 13)
        MonoText(task.text, foreground, 13, Modifier.weight(1f))
        task.due?.let { due ->
            val badge = formatDueBadge(due, today)
            MonoText(badge.label, if (badge.urgent) warning else muted, 11)
        }
    }
}

@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(hairline, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1f)
    }
}

@Composable
private fun MonoText(text: String, color: Color, size: Int, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier = modifier,
        style = TextStyle(color = color, fontFamily = FontFamily.Monospace, fontSize = size.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
