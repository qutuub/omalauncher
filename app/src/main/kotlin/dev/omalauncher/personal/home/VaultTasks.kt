package dev.omalauncher.personal.home

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal data class VaultTask(
    val lineIndex: Int,
    val rawLine: String,
    val text: String,
    val due: LocalDate?,
)

internal data class VaultSnapshot(
    val exists: Boolean = false,
    val openTasks: List<VaultTask> = emptyList(),
)

internal data class DueBadge(val label: String, val urgent: Boolean)

private val taskRegex = Regex("""^- \[([ x])\] (.+?)(?: @due\(([0-9]{4}-[0-9]{2}-[0-9]{2})\))?\r?$""")
private val shortWeekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

internal fun parseTodoMarkdown(content: String): List<VaultTask> = content
    .split('\n')
    .mapIndexedNotNull { index, raw ->
        val match = taskRegex.matchEntire(raw) ?: return@mapIndexedNotNull null
        if (match.groupValues[1] != " ") return@mapIndexedNotNull null
        val text = match.groupValues[2].trim()
        if (text.isEmpty()) return@mapIndexedNotNull null
        val dueText = match.groupValues[3]
        val due = if (dueText.isNotEmpty()) runCatching { LocalDate.parse(dueText) }.getOrNull() else null
        VaultTask(lineIndex = index, rawLine = raw, text = text, due = due)
    }

internal fun completeTaskLine(content: String, lineIndex: Int, expectedRawLine: String): String? {
    val lines = content.split('\n').toMutableList()
    if (lineIndex !in lines.indices) return null
    val current = lines[lineIndex]
    if (current.trimEnd('\r') != expectedRawLine.trimEnd('\r')) return null
    if (!current.startsWith("- [ ]")) return null
    lines[lineIndex] = "- [x]" + current.removePrefix("- [ ]")
    return lines.joinToString("\n")
}

internal fun formatDueBadge(due: LocalDate, today: LocalDate): DueBadge {
    if (due.isBefore(today)) return DueBadge(shortDate.format(due).lowercase(Locale.ROOT), urgent = true)
    if (due == today) return DueBadge("today", urgent = true)
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val nextMonday = monday.plusDays(7)
    val label = if (!due.isBefore(monday) && due.isBefore(nextMonday)) {
        shortWeekday.format(due).lowercase(Locale.ROOT)
    } else {
        shortDate.format(due).lowercase(Locale.ROOT)
    }
    return DueBadge(label, urgent = false)
}
