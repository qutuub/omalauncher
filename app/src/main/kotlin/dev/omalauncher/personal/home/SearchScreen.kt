package dev.omalauncher.personal.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.omalauncher.R
import kotlinx.coroutines.delay

private val accent = Color(0xff7aa2f7)
private const val CURSOR_BLINK_MS = 530L

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchScreen(
    findResults: (String) -> List<SearchResult>,
    profileLabel: (SearchResult) -> Int?,
    onOpen: (SearchResult) -> Unit,
    onClose: () -> Unit,
) {
    var buffer by remember { mutableStateOf(TextBuffer()) }
    var keyboardState by remember { mutableStateOf(KeyboardState()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var cursorVisible by remember { mutableStateOf(true) }

    val query = buffer.text
    val results = findResults(query)
    val typed = query.isNotBlank()
    val clampedIndex = if (results.isEmpty()) 0 else selectedIndex.coerceIn(0, results.lastIndex)

    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val inputScroll = rememberScrollState()
    var headerHeight by remember { mutableIntStateOf(0) }
    var topAreaHeight by remember { mutableIntStateOf(0) }

    fun open(result: SearchResult) {
        onClose()
        onOpen(result)
    }

    fun openSelected() {
        results.getOrNull(clampedIndex)?.let(::open)
    }

    fun handleKey(action: KeyAction) {
        val outcome = pressKey(buffer, keyboardState, action)
        val textChanged = outcome.buffer.text != buffer.text
        buffer = outcome.buffer
        keyboardState = outcome.state
        cursorVisible = true
        if (textChanged) selectedIndex = 0
        when (val event = outcome.event) {
            KeyboardEvent.Submit -> openSelected()
            KeyboardEvent.Escape -> onClose()
            KeyboardEvent.Tab -> if (results.isNotEmpty()) {
                selectedIndex = (clampedIndex + 1) % results.size
            }
            is KeyboardEvent.MoveSelection -> if (results.isNotEmpty()) {
                selectedIndex = (clampedIndex + event.delta).coerceIn(0, results.lastIndex)
            }
            KeyboardEvent.None -> Unit
        }
    }

    BackHandler { onClose() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        scroll.scrollTo(0)
        selectedIndex = 0
    }
    LaunchedEffect(buffer) {
        cursorVisible = true
        while (true) {
            delay(CURSOR_BLINK_MS)
            cursorVisible = !cursorVisible
        }
    }

    val insets = WindowInsets.displayCutout.union(WindowInsets.systemGestures)
    Column(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event -> handleHardwareKey(event, ::handleKey) },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .weight(1f)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .onSizeChanged { topAreaHeight = it.height }
                .verticalSwipe(
                    80.dp,
                    downwards = false,
                    canStart = { y, _ -> y < headerHeight || (!scroll.canScrollForward && y < topAreaHeight) },
                ) {
                    onClose()
                },
        ) {
            Column(Modifier.onSizeChanged { headerHeight = it.height }) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchText(stringResource(R.string.personal_search_prompt), accent, 18)
                    val hint = stringResource(R.string.personal_search_placeholder)
                    Row(
                        Modifier.weight(1f)
                            .horizontalScroll(inputScroll)
                            .semantics { contentDescription = hint },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (query.isEmpty()) {
                            CursorBar(cursorVisible)
                            SearchText(hint, muted, 18)
                        } else {
                            val before = query.substring(0, buffer.cursor)
                            val after = query.substring(buffer.cursor)
                            if (before.isNotEmpty()) SearchText(before, foreground, 18)
                            CursorBar(cursorVisible)
                            if (after.isNotEmpty()) SearchText(after, foreground, 18)
                        }
                    }
                    Box(
                        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { onClose() }.padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SearchText(stringResource(R.string.personal_search_close), muted, 12)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
                SearchText(
                    stringResource(if (typed) R.string.personal_search_results else R.string.personal_search_recent),
                    muted,
                    11,
                    Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp).semantics { heading() },
                    spacing = 0.88,
                )
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(horizontal = 6.dp)) {
                val showHighlight = typed || clampedIndex > 0
                results.forEachIndexed { index, result ->
                    SearchRow(result, showHighlight && index == clampedIndex, profileLabel(result)) { open(result) }
                }
                if (typed && results.isEmpty()) {
                    SearchText(stringResource(R.string.personal_search_empty), muted, 13, Modifier.padding(start = 10.dp, top = 14.dp))
                }
            }
        }
        PersonalKeyboard(keyboardState, ::handleKey)
    }
}

private fun handleHardwareKey(event: KeyEvent, onKey: (KeyAction) -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val action: KeyAction? = when (event.key) {
        Key.Enter, Key.NumPadEnter -> KeyAction.Enter
        Key.Escape -> KeyAction.Esc
        Key.Backspace -> KeyAction.Backspace
        Key.Tab -> KeyAction.Tab
        Key.DirectionLeft -> KeyAction.Left
        Key.DirectionRight -> KeyAction.Right
        Key.DirectionUp -> KeyAction.Up
        Key.DirectionDown -> KeyAction.Down
        else -> {
            val cp = event.utf16CodePoint
            if (cp >= 32 && !event.isCtrlPressed) KeyAction.Text(cp.toChar().toString()) else null
        }
    }
    if (action == null) return false
    onKey(action)
    return true
}

@Composable
private fun CursorBar(visible: Boolean) {
    Box(
        Modifier
            .width(2.dp)
            .height(20.dp)
            .background(if (visible) accent else Color.Transparent),
    )
}

@Composable
private fun SearchRow(result: SearchResult, top: Boolean, profileLabel: Int?, onOpen: () -> Unit) {
    val section = result.section
    val tag = when {
        top -> R.string.personal_search_enter
        section == null -> R.string.personal_search_app
        else -> R.string.personal_search_setting
    }
    val path = if (section == null) {
        stringResource(profileLabel ?: R.string.personal_search_android_app)
    } else {
        stringResource(R.string.personal_search_path, section)
    }
    val outline = if (top) Modifier.border(1.dp, accent, RoundedCornerShape(4.dp)) else Modifier
    Column(
        Modifier.fillMaxWidth().then(outline).clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchText(result.name, foreground, 15, Modifier.weight(1f))
            SearchText(stringResource(tag), if (top) accent else muted, 11)
        }
        SearchText(path, muted, 11)
    }
}

@Composable
private fun SearchText(text: String, color: Color, size: Int, modifier: Modifier = Modifier, spacing: Double = 0.0) {
    BasicText(
        text,
        modifier,
        style = TextStyle(color = color, fontFamily = FontFamily.Monospace, fontSize = size.sp, letterSpacing = spacing.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
