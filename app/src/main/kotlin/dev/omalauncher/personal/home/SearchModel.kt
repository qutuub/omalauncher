package dev.omalauncher.personal.home

internal data class SearchResult(val id: String, val name: String, val section: String? = null)

internal const val RECENT_APP_LIMIT = 5
private const val SEARCH_RESULT_LIMIT = 7
// AndroidAppCatalog keys are flattened components with an optional @profile serial.
private val appKey = Regex("[^/\\s@]+/[^/\\s@]+(?:@[0-9]+)?")
private val wordBreak = Regex("[\\s-]+")

internal fun readRecentAppIds(stored: String?): List<String> = stored.orEmpty().lineSequence()
    .filter(appKey::matches)
    .distinct()
    .take(RECENT_APP_LIMIT)
    .toList()

internal fun searchResults(
    query: String,
    apps: List<SearchResult>,
    settings: List<SearchResult>,
    recents: List<String>,
    resolves: (SearchResult) -> Boolean,
): List<SearchResult> {
    val term = query.trim().lowercase()
    if (term.isEmpty()) {
        val byId = apps.associateBy { it.id }
        return (recents.mapNotNull(byId::get) + apps.sortedBy { it.name.lowercase() })
            .distinctBy { it.id }
            .take(RECENT_APP_LIMIT)
    }
    return (apps + settings.filter(resolves))
        .mapNotNull { result -> matchRank(result.name.lowercase(), term)?.let { result to it } }
        .sortedWith(compareBy({ it.second }, { it.first.section != null }, { it.first.name.lowercase() }))
        .take(SEARCH_RESULT_LIMIT)
        .map { it.first }
}

private fun matchRank(name: String, term: String): Int? = when {
    name.startsWith(term) -> 0
    wordBreak.splitToSequence(name).any { it.startsWith(term) } -> 1
    term in name -> 2
    else -> null
}
