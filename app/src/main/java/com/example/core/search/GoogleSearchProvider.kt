package com.example.core.search

import com.example.core.model.SearchResult
import com.example.core.model.SearchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

class GoogleSearchProvider(
    private val client: OkHttpClient,
    private val apiKeyProvider: () -> String? = { null },
    private val searchEngineIdProvider: () -> String? = { null }
) : SearchProvider {

    override val providerName: String = "Google Search"

    override suspend fun search(
        query: String,
        type: SearchType,
        page: Int
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        val searchEngineId = searchEngineIdProvider()

        if (apiKey.isNullOrBlank() || searchEngineId.isNullOrBlank()) {
            // If backend / environment key is not configured, signal failover to next provider
            return@withContext Result.failure(IllegalStateException("Google Search API key or Engine ID not configured"))
        }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val startIndex = (page - 1) * 10 + 1
            val searchTypeParam = if (type == SearchType.IMAGES) "&searchType=image" else ""
            
            val url = "https://www.googleapis.com/customsearch/v1?key=$apiKey&cx=$searchEngineId&q=$encodedQuery&start=$startIndex$searchTypeParam"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Google Search API failed with code ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.success(emptyList())
            val json = JSONObject(responseBody)
            val items = json.optJSONArray("items") ?: return@withContext Result.success(emptyList())
            
            val results = mutableListOf<SearchResult>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val title = item.optString("title", "")
                val itemUrl = item.optString("link", "")
                val displayUrl = item.optString("displayLink", itemUrl)
                val snippet = item.optString("snippet", "")
                
                var thumbnail: String? = null
                val pagemap = item.optJSONObject("pagemap")
                if (pagemap != null) {
                    val cseImage = pagemap.optJSONArray("cse_image")
                    if (cseImage != null && cseImage.length() > 0) {
                        thumbnail = cseImage.getJSONObject(0).optString("src")
                    }
                }

                val domain = try {
                    java.net.URI(itemUrl).host ?: displayUrl
                } catch (_: Exception) {
                    displayUrl
                }

                results.add(
                    SearchResult(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        url = itemUrl,
                        displayUrl = displayUrl,
                        snippet = snippet,
                        source = domain,
                        thumbnail = thumbnail,
                        domain = domain,
                        type = type
                    )
                )
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=chrome&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val jsonArray = org.json.JSONArray(body)
            if (jsonArray.length() > 1) {
                val suggestionsArray = jsonArray.getJSONArray(1)
                val list = mutableListOf<String>()
                for (i in 0 until suggestionsArray.length()) {
                    list.add(suggestionsArray.getString(i))
                }
                return@withContext list
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        !apiKeyProvider().isNullOrBlank() && !searchEngineIdProvider().isNullOrBlank()
    }
}
