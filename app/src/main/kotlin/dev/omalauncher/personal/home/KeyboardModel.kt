package dev.omalauncher.personal.home

internal data class TextBuffer(val text: String = "", val cursor: Int = text.length) {
    init {
        require(cursor in 0..text.length) { "cursor $cursor out of range 0..${text.length}" }
    }

    fun insert(value: String): TextBuffer {
        if (value.isEmpty()) return this
        val next = text.substring(0, cursor) + value + text.substring(cursor)
        return TextBuffer(next, cursor + value.length)
    }

    fun backspace(): TextBuffer {
        if (cursor == 0) return this
        val next = text.removeRange(cursor - 1, cursor)
        return TextBuffer(next, cursor - 1)
    }

    fun deleteWordBackward(): TextBuffer {
        if (cursor == 0) return this
        var i = cursor
        while (i > 0 && text[i - 1].isWhitespace()) i--
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        return TextBuffer(text.removeRange(i, cursor), i)
    }

    fun deleteToStart(): TextBuffer {
        if (cursor == 0) return this
        return TextBuffer(text.substring(cursor), 0)
    }

    fun deleteToEnd(): TextBuffer {
        if (cursor == text.length) return this
        return TextBuffer(text.substring(0, cursor), cursor)
    }

    fun moveLeft(): TextBuffer = if (cursor > 0) copy(cursor = cursor - 1) else this

    fun moveRight(): TextBuffer = if (cursor < text.length) copy(cursor = cursor + 1) else this

    fun moveStart(): TextBuffer = if (cursor > 0) copy(cursor = 0) else this

    fun moveEnd(): TextBuffer = if (cursor < text.length) copy(cursor = text.length) else this
}

internal enum class KeyboardLayer { Letters, Symbols, MoreSymbols }

internal enum class ShiftMode { Off, Once, Locked }

internal data class KeyboardState(
    val layer: KeyboardLayer = KeyboardLayer.Letters,
    val shift: ShiftMode = ShiftMode.Off,
    val ctrl: Boolean = false,
)

internal sealed interface KeyAction {
    data class Text(val value: String) : KeyAction
    data object Shift : KeyAction
    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data class Layer(val target: KeyboardLayer) : KeyAction
    data object Esc : KeyAction
    data object Ctrl : KeyAction
    data object Tab : KeyAction
    data object Left : KeyAction
    data object Down : KeyAction
    data object Up : KeyAction
    data object Right : KeyAction
}

internal data class KeySpec(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val repeats: Boolean = false,
    val accent: Boolean = false,
)

internal sealed interface KeyboardEvent {
    data object None : KeyboardEvent
    data object Submit : KeyboardEvent
    data object Escape : KeyboardEvent
    data object Tab : KeyboardEvent
    data class MoveSelection(val delta: Int) : KeyboardEvent
}

internal data class KeyboardOutcome(
    val buffer: TextBuffer,
    val state: KeyboardState,
    val event: KeyboardEvent = KeyboardEvent.None,
)

internal fun pressKey(buffer: TextBuffer, state: KeyboardState, action: KeyAction): KeyboardOutcome = when (action) {
    KeyAction.Ctrl -> KeyboardOutcome(buffer, state.copy(ctrl = !state.ctrl))
    KeyAction.Shift -> {
        val nextShift = when (state.shift) {
            ShiftMode.Off -> ShiftMode.Once
            ShiftMode.Once -> ShiftMode.Locked
            ShiftMode.Locked -> ShiftMode.Off
        }
        KeyboardOutcome(buffer, state.copy(shift = nextShift, ctrl = false))
    }
    is KeyAction.Layer -> KeyboardOutcome(
        buffer,
        state.copy(
            layer = action.target,
            shift = if (action.target == KeyboardLayer.Letters) state.shift else ShiftMode.Off,
            ctrl = false,
        ),
    )
    KeyAction.Esc -> KeyboardOutcome(buffer, state.copy(ctrl = false), KeyboardEvent.Escape)
    KeyAction.Tab -> KeyboardOutcome(buffer, state.copy(ctrl = false), KeyboardEvent.Tab)
    KeyAction.Enter -> KeyboardOutcome(buffer, state.copy(ctrl = false), KeyboardEvent.Submit)
    KeyAction.Left -> KeyboardOutcome(buffer.moveLeft(), state.copy(ctrl = false))
    KeyAction.Right -> KeyboardOutcome(buffer.moveRight(), state.copy(ctrl = false))
    KeyAction.Up -> KeyboardOutcome(buffer, state.copy(ctrl = false), KeyboardEvent.MoveSelection(-1))
    KeyAction.Down -> KeyboardOutcome(buffer, state.copy(ctrl = false), KeyboardEvent.MoveSelection(1))
    KeyAction.Backspace -> {
        val next = if (state.ctrl) buffer.deleteWordBackward() else buffer.backspace()
        KeyboardOutcome(next, state.copy(ctrl = false))
    }
    KeyAction.Space -> KeyboardOutcome(buffer.insert(" "), state.copy(ctrl = false))
    is KeyAction.Text -> {
        if (state.ctrl) {
            handleCtrlText(buffer, state, action.value)
        } else {
            val nextShift = if (state.shift == ShiftMode.Once) ShiftMode.Off else state.shift
            KeyboardOutcome(buffer.insert(action.value), state.copy(shift = nextShift))
        }
    }
}

private fun handleCtrlText(buffer: TextBuffer, state: KeyboardState, raw: String): KeyboardOutcome {
    val cleared = state.copy(ctrl = false)
    return when (raw.lowercase()) {
        "a" -> KeyboardOutcome(buffer.moveStart(), cleared)
        "e" -> KeyboardOutcome(buffer.moveEnd(), cleared)
        "u" -> KeyboardOutcome(buffer.deleteToStart(), cleared)
        "k" -> KeyboardOutcome(buffer.deleteToEnd(), cleared)
        "w" -> KeyboardOutcome(buffer.deleteWordBackward(), cleared)
        "h" -> KeyboardOutcome(buffer.backspace(), cleared)
        "b" -> KeyboardOutcome(buffer.moveLeft(), cleared)
        "f" -> KeyboardOutcome(buffer.moveRight(), cleared)
        "n" -> KeyboardOutcome(buffer, cleared, KeyboardEvent.MoveSelection(1))
        "p" -> KeyboardOutcome(buffer, cleared, KeyboardEvent.MoveSelection(-1))
        "c", "[" -> KeyboardOutcome(buffer, cleared, KeyboardEvent.Escape)
        "m", "j" -> KeyboardOutcome(buffer, cleared, KeyboardEvent.Submit)
        else -> KeyboardOutcome(buffer, cleared)
    }
}

internal val codingKeys: List<KeySpec> = listOf(
    KeySpec("esc", KeyAction.Esc, weight = 1.15f),
    KeySpec("ctrl", KeyAction.Ctrl, weight = 1.25f),
    KeySpec("tab", KeyAction.Tab, weight = 1.15f),
    KeySpec("←", KeyAction.Left, repeats = true),
    KeySpec("↓", KeyAction.Down, repeats = true),
    KeySpec("↑", KeyAction.Up, repeats = true),
    KeySpec("→", KeyAction.Right, repeats = true),
)

internal fun keyRows(layer: KeyboardLayer, shift: ShiftMode): List<List<KeySpec>> = when (layer) {
    KeyboardLayer.Letters -> {
        val upper = shift != ShiftMode.Off
        listOf(
            charRow("qwertyuiop", upper),
            charRow("asdfghjkl", upper),
            listOf(KeySpec("⇧", KeyAction.Shift, weight = 1.45f)) +
                charRow("zxcvbnm", upper) +
                listOf(KeySpec("⌫", KeyAction.Backspace, weight = 1.45f, repeats = true)),
            listOf(
                KeySpec("123", KeyAction.Layer(KeyboardLayer.Symbols), weight = 1.45f),
                KeySpec("/", KeyAction.Text("/"), weight = 0.9f),
                KeySpec("space", KeyAction.Space, weight = 4.1f),
                KeySpec(".", KeyAction.Text("."), weight = 0.9f),
                KeySpec("↵", KeyAction.Enter, weight = 1.65f, accent = true),
            ),
        )
    }
    KeyboardLayer.Symbols -> listOf(
        charRow("1234567890", false),
        charRow("-/:;()\$&@\"", false),
        listOf(KeySpec("=\\<", KeyAction.Layer(KeyboardLayer.MoreSymbols), weight = 1.45f)) +
            charRow(".,?!'_#", false) +
            listOf(KeySpec("⌫", KeyAction.Backspace, weight = 1.45f, repeats = true)),
        listOf(
            KeySpec("abc", KeyAction.Layer(KeyboardLayer.Letters), weight = 1.45f),
            KeySpec("[", KeyAction.Text("["), weight = 0.9f),
            KeySpec("space", KeyAction.Space, weight = 4.1f),
            KeySpec("]", KeyAction.Text("]"), weight = 0.9f),
            KeySpec("↵", KeyAction.Enter, weight = 1.65f, accent = true),
        ),
    )
    KeyboardLayer.MoreSymbols -> listOf(
        charRow("~`!@#\$%^&*", false),
        charRow("[]{}()<>\\|", false),
        listOf(KeySpec("123", KeyAction.Layer(KeyboardLayer.Symbols), weight = 1.45f)) +
            charRow("=+-_:;'", false) +
            listOf(KeySpec("⌫", KeyAction.Backspace, weight = 1.45f, repeats = true)),
        listOf(
            KeySpec("abc", KeyAction.Layer(KeyboardLayer.Letters), weight = 1.45f),
            KeySpec("/", KeyAction.Text("/"), weight = 0.9f),
            KeySpec("space", KeyAction.Space, weight = 4.1f),
            KeySpec(".", KeyAction.Text("."), weight = 0.9f),
            KeySpec("↵", KeyAction.Enter, weight = 1.65f, accent = true),
        ),
    )
}

private fun charRow(chars: String, upper: Boolean): List<KeySpec> = chars.map { ch ->
    val text = if (upper) ch.uppercase() else ch.toString()
    KeySpec(text, KeyAction.Text(text))
}
