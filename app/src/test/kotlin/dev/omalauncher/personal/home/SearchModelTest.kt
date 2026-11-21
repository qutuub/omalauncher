package dev.omalauncher.personal.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchModelTest {
    private fun app(name: String, id: String = name) = SearchResult(id, name)
    private fun setting(name: String) = SearchResult("setting:$name", name, "general")

    @Test
    fun ranksNamePrefixBeforeWordPrefixBeforeSubstringIgnoringCase() {
        val contains = app("Backdrop")
        val wordPrefix = app("Photo-Drop")
        val namePrefix = setting("Drop options")
        assertEquals(
            listOf(namePrefix, wordPrefix, contains),
            searchResults("  DrOP  ", listOf(contains, wordPrefix), listOf(namePrefix), emptyList()) { true },
        )
    }

    @Test
    fun breaksRankTiesByKindThenAlphabetically() {
        val zeta = app("Zeta notes")
        val beta = app("beta notes")
        val gamma = setting("Gamma notes")
        val alpha = setting("alpha notes")
        assertEquals(
            listOf(beta, zeta, alpha, gamma),
            searchResults("notes", listOf(zeta, beta), listOf(gamma, alpha), emptyList()) { true },
        )
    }

    @Test
    fun emptyQueryUsesRecentActivityIdsThenFillsAlphabeticallyUpToFive() {
        val work = app("Mail", "mail@10")
        val personal = app("Mail", "mail")
        val alpha = app("alpha")
        val beta = app("Beta")
        val gamma = app("gamma")
        val apps = listOf(app("zeta"), personal, gamma, work, beta, alpha)
        assertEquals(
            listOf(work, personal, alpha, beta, gamma),
            searchResults(" ", apps, listOf(setting("Alpha")), listOf(work.id, "removed", personal.id, work.id)) { true },
        )
    }

    @Test
    fun limitsTypedResultsToSevenAfterFilteringUnresolvableSettings() {
        val apps = (2..9).reversed().map { app("Item $it") }
        val hidden = setting("Item 0")
        val visible = setting("Item 1")
        assertEquals(
            apps.reversed().take(7),
            searchResults("item", apps, listOf(hidden, visible), emptyList()) { it != hidden },
        )
        assertEquals(
            listOf(visible),
            searchResults("item", emptyList(), listOf(hidden, visible), emptyList()) { it != hidden },
        )
    }

    @Test
    fun readsAtMostFiveDistinctValidRecentIds() {
        val ids = (1..6).map { "dev.example/.Main@$it" }
        val stored = (listOf("", "malformed", "dev.example/", "dev.example/.Main@bad", ids.first()) + ids)
            .joinToString("\n")
        assertEquals(ids.take(5), readRecentAppIds(stored))
        assertEquals(emptyList<String>(), readRecentAppIds(null))
    }

    @Test
    fun noMatchReturnsAnEmptyList() {
        assertEquals(
            emptyList<SearchResult>(),
            searchResults("missing", listOf(app("Clock")), listOf(setting("Display")), emptyList()) { true },
        )
    }
}
