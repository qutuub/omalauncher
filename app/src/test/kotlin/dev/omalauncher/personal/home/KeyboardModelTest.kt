package dev.omalauncher.personal.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardModelTest {
    @Test
    fun insertsDeletesAndMovesCursorInMiddle() {
        var out = KeyboardOutcome(TextBuffer(), KeyboardState())
        out = pressKey(out.buffer, out.state, KeyAction.Text("a"))
        out = pressKey(out.buffer, out.state, KeyAction.Text("c"))
        out = pressKey(out.buffer, out.state, KeyAction.Left)
        out = pressKey(out.buffer, out.state, KeyAction.Text("b"))
        assertEquals(TextBuffer("abc", 2), out.buffer)

        out = pressKey(out.buffer, out.state, KeyAction.Backspace)
        assertEquals(TextBuffer("ac", 1), out.buffer)

        out = pressKey(out.buffer, out.state, KeyAction.Right)
        assertEquals(TextBuffer("ac", 2), out.buffer)
    }

    @Test
    fun shiftCyclesOnceLockedAndOff() {
        var state = KeyboardState()
        state = pressKey(TextBuffer(), state, KeyAction.Shift).state
        assertEquals(ShiftMode.Once, state.shift)
        // Typing a letter resets ShiftMode.Once back to Off.
        var out = pressKey(TextBuffer(), state, KeyAction.Text("A"))
        assertEquals("A", out.buffer.text)
        assertEquals(ShiftMode.Off, out.state.shift)

        // Two Shift taps lock caps until tapped again.
        state = pressKey(TextBuffer(), KeyboardState(), KeyAction.Shift).state
        state = pressKey(TextBuffer(), state, KeyAction.Shift).state
        assertEquals(ShiftMode.Locked, state.shift)
        out = pressKey(TextBuffer(), state, KeyAction.Text("B"))
        assertEquals(ShiftMode.Locked, out.state.shift)
        state = pressKey(out.buffer, out.state, KeyAction.Shift).state
        assertEquals(ShiftMode.Off, state.shift)
    }

    @Test
    fun ctrlShortcutsEditAndNavigate() {
        val ctrlOn = KeyboardState(ctrl = true)
        assertEquals(TextBuffer("hello world", 0), pressKey(TextBuffer("hello world", 11), ctrlOn, KeyAction.Text("a")).buffer)
        assertEquals(TextBuffer("hello world", 11), pressKey(TextBuffer("hello world", 0), ctrlOn, KeyAction.Text("e")).buffer)
        assertEquals(TextBuffer("hello ", 6), pressKey(TextBuffer("hello world", 11), ctrlOn, KeyAction.Text("w")).buffer)
        assertEquals(TextBuffer("world", 0), pressKey(TextBuffer("hello world", 6), ctrlOn, KeyAction.Text("u")).buffer)
        assertEquals(TextBuffer("hello ", 6), pressKey(TextBuffer("hello world", 6), ctrlOn, KeyAction.Text("k")).buffer)
        assertEquals(KeyboardEvent.MoveSelection(1), pressKey(TextBuffer(), ctrlOn, KeyAction.Text("n")).event)
        assertEquals(KeyboardEvent.MoveSelection(-1), pressKey(TextBuffer(), ctrlOn, KeyAction.Text("p")).event)
        assertEquals(KeyboardEvent.Escape, pressKey(TextBuffer(), ctrlOn, KeyAction.Text("c")).event)
        assertEquals(KeyboardEvent.Submit, pressKey(TextBuffer(), ctrlOn, KeyAction.Text("m")).event)

        // Ctrl always unlatches after a chord.
        val after = pressKey(TextBuffer("ab", 2), ctrlOn, KeyAction.Backspace)
        assertEquals(TextBuffer("", 0), after.buffer)
        assertFalse(after.state.ctrl)
    }

    @Test
    fun codingKeysAndLayersCoverTerminalControlsAndSymbols() {
        assertEquals(listOf("esc", "ctrl", "tab", "←", "↓", "↑", "→"), codingKeys.map { it.label })

        val letters = keyRows(KeyboardLayer.Letters, ShiftMode.Off).flatten().mapNotNull { (it.action as? KeyAction.Text)?.value }.toSet()
        assertTrue(('a'..'z').all { it.toString() in letters })

        val upper = keyRows(KeyboardLayer.Letters, ShiftMode.Once).flatten().mapNotNull { (it.action as? KeyAction.Text)?.value }.toSet()
        assertTrue(('A'..'Z').all { it.toString() in upper })

        val symbols = (
            keyRows(KeyboardLayer.Symbols, ShiftMode.Off) +
                keyRows(KeyboardLayer.MoreSymbols, ShiftMode.Off)
            ).flatten().mapNotNull { (it.action as? KeyAction.Text)?.value }.toSet()
        assertTrue(('0'..'9').all { it.toString() in symbols })
        for (ch in listOf("-", "/", ":", ";", "(", ")", "[", "]", "{", "}", "@", "#", "_", "~", "`", "\\", "|", "=", "+")) {
            assertTrue("missing symbol $ch", ch in symbols)
        }
    }

    @Test
    fun layerSwitchResetsShiftAndCtrl() {
        val state = KeyboardState(layer = KeyboardLayer.Letters, shift = ShiftMode.Locked, ctrl = true)
        val out = pressKey(TextBuffer(), state, KeyAction.Layer(KeyboardLayer.Symbols))
        assertEquals(KeyboardLayer.Symbols, out.state.layer)
        assertEquals(ShiftMode.Off, out.state.shift)
        assertFalse(out.state.ctrl)
    }
}
