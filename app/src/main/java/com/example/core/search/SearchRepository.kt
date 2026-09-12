package com.example.core.search

import com.example.core.model.SearchResult
import com.example.core.model.SearchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class CachedSearchResult(
    val results: List<SearchResult>,
    val timestamp: Long
)

class SearchRepository(
    private val providers: List<SearchProvider>
) {
    private val searchCache = ConcurrentHashMap<String, CachedSearchResult>()
    private val CACHE_TTL_MILLIS = 10 * 60 * 1000L // 10 minutes

    suspend fun search(
        query: String,
        type: SearchType = SearchType.WEB,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        val cacheKey = "${type.name}_${page}_${trimmedQuery.lowercase()}"

        if (!forceRefresh) {
            val cached = searchCache[cacheKey]
            if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MILLIS) {
                return@withContext Result.success(cached.results)
            }
        }

        var lastException: Throwable? = null

        for (provider in providers) {
            try {
                val result = provider.search(trimmedQuery, type, page)
                if (result.isSuccess) {
                    val rawResults = result.getOrNull() ?: emptyList()
                    val normalized = normalizeAndRank(rawResults, trimmedQuery)
                    searchCache[cacheKey] = CachedSearchResult(normalized, System.currentTimeMillis())
                    return@withContext Result.success(normalized)
                } else {
                    lastException = result.exceptionOrNull()
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        Result.failure(lastException ?: Exception("All search providers failed to retrieve results"))
    }

    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        for (provider in providers) {
            try {
                val suggestions = provider.suggest(trimmed)
                if (suggestions.isNotEmpty()) {
                    return@withContext suggestions.distinct().take(8)
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }

    private fun normalizeAndRank(results: List<SearchResult>, query: String): List<SearchResult> {
        val seenUrls = mutableSetOf<String>()
        val distinctResults = mutableListOf<SearchResult>()
        val queryTerms = query.lowercase().split(" ").filter { it.isNotBlank() }

        for (res in results) {
            // Strip tracking params like utm_source, fbclid
            val cleanUrl = stripTrackingParams(res.url)
            if (seenUrls.add(cleanUrl)) {
                distinctResults.add(res.copy(url = cleanUrl))
            }
        }

        // Rank by query keyword presence in title and snippet
        return distinctResults.sortedByDescending { item ->
            var score = 0
            val lowerTitle = item.title.lowercase()
            val lowerSnippet = item.snippet.lowercase()

            for (term in queryTerms) {
                if (lowerTitle.contains(term)) score += 10
                if (lowerSnippet.contains(term)) score += 4
            }
            score
        }
    }

    private fun stripTrackingParams(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val builder = uri.buildUpon().clearQuery()
            for (name in uri.queryParameterNames) {
                if (!name.startsWith("utm_") &&
                    name != "fbclid" &&
                    name != "gclid" &&
                    name != "ref" &&
                    name != "source"
                ) {
                    builder.appendQueryParameter(name, uri.getQueryParameter(name))
                }
            }
            builder.build().toString()
        } catch (_: Exception) {
            url
        }
    }
}
