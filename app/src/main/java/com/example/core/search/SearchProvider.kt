package com.example.core.search

import com.example.core.model.SearchResult
import com.example.core.model.SearchType

interface SearchProvider {
    val providerName: String
    suspend fun search(query: String, type: SearchType = SearchType.WEB, page: Int = 1): Result<List<SearchResult>>
    suspend fun suggest(query: String): List<String>
    suspend fun healthCheck(): Boolean
}
