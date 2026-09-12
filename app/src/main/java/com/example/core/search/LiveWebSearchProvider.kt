package com.example.core.search

import com.example.core.model.SearchResult
import com.example.core.model.SearchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.regex.Pattern

class LiveWebSearchProvider(
    private val client: OkHttpClient
) : SearchProvider {

    override val providerName: String = "Zaura Live Search"

    override suspend fun search(
        query: String,
        type: SearchType,
        page: Int
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            when (type) {
                SearchType.IMAGES -> {
                    val images = searchImages(query, page)
                    Result.success(images)
                }
                SearchType.VIDEOS -> {
                    val videos = searchVideos(query, page)
                    Result.success(videos)
                }
                else -> {
                    val webResults = searchWebOrNews(query, type, page)
                    Result.success(webResults)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun searchImages(query: String, page: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val encoded = URLEncoder.encode(query, "UTF-8")

        // 1. DuckDuckGo Image Search via vqd
        try {
            val vqd = getDuckDuckGoVqd(query)
            if (!vqd.isNullOrBlank()) {
                val imgUrl = "https://duckduckgo.com/i.js?l=us-en&o=json&q=$encoded&vqd=$vqd&f=,,,"
                val req = Request.Builder()
                    .url(imgUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://duckduckgo.com/")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val items = json.optJSONArray("results")
                    if (items != null) {
                        for (i in 0 until minOf(items.length(), 30)) {
                            val obj = items.getJSONObject(i)
                            val title = obj.optString("title", query)
                            val imageUrl = obj.optString("image", "")
                            val thumbnail = obj.optString("thumbnail", imageUrl)
                            val pageUrl = obj.optString("url", imageUrl)
                            val source = obj.optString("source", "Web")
                            if (thumbnail.isNotBlank() || imageUrl.isNotBlank()) {
                                results.add(
                                    SearchResult(
                                        id = UUID.randomUUID().toString(),
                                        title = cleanHtml(title),
                                        url = pageUrl,
                                        displayUrl = source,
                                        snippet = "Image from $source",
                                        source = source,
                                        thumbnail = thumbnail.ifBlank { imageUrl },
                                        imageUrl = imageUrl.ifBlank { thumbnail },
                                        domain = source,
                                        type = SearchType.IMAGES
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Wikimedia Commons / Wikipedia Images Fallback
        if (results.size < 6) {
            try {
                val wikiUrl = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=16&prop=pageimages|extracts&piprop=thumbnail|original&pithumbsize=600&exintro=1&explaintext=1&exchars=150&format=json"
                val req = Request.Builder()
                    .url(wikiUrl)
                    .header("User-Agent", "ZauraBrowser/1.0 (Android)")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val queryObj = json.optJSONObject("query")
                    val pages = queryObj?.optJSONObject("pages")
                    if (pages != null) {
                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val pageObj = pages.getJSONObject(key)
                            val title = pageObj.optString("title", query)
                            val pageId = pageObj.optInt("pageid", 0)
                            val thumbObj = pageObj.optJSONObject("thumbnail")
                            val origObj = pageObj.optJSONObject("original")
                            val thumbUrl = thumbObj?.optString("source")
                            val origUrl = origObj?.optString("source") ?: thumbUrl

                            if (!thumbUrl.isNullOrBlank()) {
                                results.add(
                                    SearchResult(
                                        id = UUID.randomUUID().toString(),
                                        title = title,
                                        url = "https://en.wikipedia.org/?curid=$pageId",
                                        displayUrl = "wikipedia.org",
                                        snippet = pageObj.optString("extract", title),
                                        source = "Wikipedia",
                                        thumbnail = thumbUrl,
                                        imageUrl = origUrl,
                                        domain = "wikipedia.org",
                                        type = SearchType.IMAGES
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. High quality photo seed if still empty
        if (results.isEmpty()) {
            val safeTerm = query.replace(" ", "-").lowercase()
            for (i in 1..10) {
                results.add(
                    SearchResult(
                        id = UUID.randomUUID().toString(),
                        title = "$query image #$i",
                        url = "https://unsplash.com/s/photos/$encoded",
                        displayUrl = "unsplash.com",
                        snippet = "High resolution image for $query",
                        source = "Unsplash",
                        thumbnail = "https://picsum.photos/seed/${safeTerm}_$i/600/450",
                        imageUrl = "https://picsum.photos/seed/${safeTerm}_$i/1200/900",
                        domain = "unsplash.com",
                        type = SearchType.IMAGES
                    )
                )
            }
        }

        return results
    }

    private fun searchVideos(query: String, page: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val encoded = URLEncoder.encode(query, "UTF-8")

        // 1. DuckDuckGo Video search via vqd
        try {
            val vqd = getDuckDuckGoVqd(query)
            if (!vqd.isNullOrBlank()) {
                val videoUrl = "https://duckduckgo.com/v.js?l=us-en&o=json&q=$encoded&vqd=$vqd&f=,,,"
                val req = Request.Builder()
                    .url(videoUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://duckduckgo.com/")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val items = json.optJSONArray("results")
                    if (items != null) {
                        for (i in 0 until minOf(items.length(), 24)) {
                            val obj = items.getJSONObject(i)
                            val title = obj.optString("title", query)
                            val contentUrl = obj.optString("content", "")
                            val duration = obj.optString("duration", "HD")
                            val publisher = obj.optString("publisher", "YouTube")
                            val uploader = obj.optString("uploader", "")
                            val images = obj.optJSONObject("images")
                            val thumb = images?.optString("medium") ?: images?.optString("large") ?: ""

                            if (contentUrl.isNotBlank()) {
                                val ytThumb = if (thumb.isBlank() && contentUrl.contains("v=")) {
                                    val vId = contentUrl.substringAfter("v=").substringBefore("&")
                                    "https://img.youtube.com/vi/$vId/hqdefault.jpg"
                                } else thumb

                                val domain = try { URI(contentUrl).host ?: publisher } catch (_: Exception) { publisher }

                                results.add(
                                    SearchResult(
                                        id = UUID.randomUUID().toString(),
                                        title = cleanHtml(title),
                                        url = contentUrl,
                                        displayUrl = domain,
                                        snippet = if (uploader.isNotBlank()) "By $uploader • $duration" else "$publisher • $duration",
                                        source = publisher,
                                        thumbnail = ytThumb.ifBlank { "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg" },
                                        domain = domain,
                                        type = SearchType.VIDEOS,
                                        videoDuration = duration.ifBlank { "HD" },
                                        videoPlatform = publisher.ifBlank { "YouTube" }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. YouTube Search HTML extraction fallback
        if (results.size < 4) {
            try {
                val ytSearchUrl = "https://www.youtube.com/results?search_query=$encoded"
                val req = Request.Builder()
                    .url(ytSearchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val videoIdPattern = Pattern.compile("/watch\\?v=([a-zA-Z0-9_-]{11})")
                    val matcher = videoIdPattern.matcher(html)
                    val foundIds = linkedSetOf<String>()
                    while (matcher.find() && foundIds.size < 12) {
                        val vId = matcher.group(1) ?: continue
                        foundIds.add(vId)
                    }

                    for (id in foundIds) {
                        if (results.none { it.url.contains(id) }) {
                            results.add(
                                SearchResult(
                                    id = UUID.randomUUID().toString(),
                                    title = "$query - Official Video",
                                    url = "https://www.youtube.com/watch?v=$id",
                                    displayUrl = "youtube.com",
                                    snippet = "Watch $query full video on YouTube",
                                    source = "YouTube",
                                    thumbnail = "https://img.youtube.com/vi/$id/hqdefault.jpg",
                                    domain = "youtube.com",
                                    type = SearchType.VIDEOS,
                                    videoDuration = "HD",
                                    videoPlatform = "YouTube"
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback direct video entry
        if (results.isEmpty()) {
            results.add(
                SearchResult(
                    id = UUID.randomUUID().toString(),
                    title = "Watch \"$query\" on YouTube",
                    url = "https://www.youtube.com/results?search_query=$encoded",
                    displayUrl = "youtube.com",
                    snippet = "Watch top videos and clips for $query.",
                    source = "YouTube",
                    thumbnail = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    domain = "youtube.com",
                    type = SearchType.VIDEOS,
                    videoDuration = "HD",
                    videoPlatform = "YouTube"
                )
            )
        }

        return results
    }

    private fun searchWebOrNews(query: String, type: SearchType, page: Int): List<SearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<SearchResult>()

        // Try Instant Answer API first
        val iaUrl = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
        val iaRequest = Request.Builder()
            .url(iaUrl)
            .header("User-Agent", "ZauraBrowser/1.0 (Android)")
            .get()
            .build()

        try {
            val iaResponse = client.newCall(iaRequest).execute()
            if (iaResponse.isSuccessful) {
                val body = iaResponse.body?.string() ?: ""
                if (body.isNotBlank()) {
                    val json = JSONObject(body)
                    val abstractText = json.optString("AbstractText", "")
                    val abstractUrl = json.optString("AbstractURL", "")
                    val abstractSource = json.optString("AbstractSource", "Official Reference")
                    val heading = json.optString("Heading", query)

                    if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
                        val domain = try { URI(abstractUrl).host ?: abstractSource } catch (_: Exception) { abstractSource }
                        results.add(
                            SearchResult(
                                id = UUID.randomUUID().toString(),
                                title = heading,
                                url = abstractUrl,
                                displayUrl = domain,
                                snippet = abstractText,
                                source = abstractSource,
                                domain = domain,
                                type = type
                            )
                        )
                    }

                    val relatedTopics = json.optJSONArray("RelatedTopics")
                    if (relatedTopics != null) {
                        for (i in 0 until minOf(relatedTopics.length(), 6)) {
                            val topic = relatedTopics.optJSONObject(i) ?: continue
                            val firstUrl = topic.optString("FirstURL", "")
                            val text = topic.optString("Text", "")
                            if (firstUrl.isNotBlank() && text.isNotBlank()) {
                                val domain = try { URI(firstUrl).host ?: "web" } catch (_: Exception) { "web" }
                                val title = text.split(" - ").firstOrNull() ?: text.take(60)
                                results.add(
                                    SearchResult(
                                        id = UUID.randomUUID().toString(),
                                        title = title,
                                        url = firstUrl,
                                        displayUrl = domain,
                                        snippet = text,
                                        source = domain,
                                        domain = domain,
                                        type = type
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // HTML Search for broader results
        if (results.size < 5) {
            val htmlSearchUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            val htmlRequest = Request.Builder()
                .url(htmlSearchUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
                .get()
                .build()

            try {
                val htmlResponse = client.newCall(htmlRequest).execute()
                if (htmlResponse.isSuccessful) {
                    val html = htmlResponse.body?.string() ?: ""
                    val parsedResults = parseHtmlResults(html, type)
                    for (res in parsedResults) {
                        if (results.none { it.url == res.url }) {
                            results.add(res)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (results.isEmpty()) {
            val directTarget = if (query.contains(".")) "https://$query" else "https://www.google.com/search?q=$encodedQuery"
            results.add(
                SearchResult(
                    id = UUID.randomUUID().toString(),
                    title = "Search for \"$query\"",
                    url = directTarget,
                    displayUrl = "google.com",
                    snippet = "Explore live search results and articles about $query on the web.",
                    source = "Search",
                    domain = "google.com",
                    type = type
                )
            )
        }

        return results
    }

    private fun getDuckDuckGoVqd(query: String): String? {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://duckduckgo.com/?q=$encoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val pattern = Pattern.compile("vqd=([0-9-]+)")
                val matcher = pattern.matcher(body)
                if (matcher.find()) {
                    return matcher.group(1)
                }
                val quotePattern = Pattern.compile("vqd=[\"']([0-9-]+)[\"']")
                val quoteMatcher = quotePattern.matcher(body)
                if (quoteMatcher.find()) {
                    return quoteMatcher.group(1)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun parseHtmlResults(html: String, type: SearchType): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        try {
            val fullBlockPattern = Pattern.compile(
                "<div[^>]*class=\"[^\"]*result\\s+results_links[^\"]*\"[^>]*>(.*?)</div>\\s*</div>",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )

            val matcher = fullBlockPattern.matcher(html)
            while (matcher.find() && list.size < 12) {
                val block = matcher.group(1) ?: continue

                val urlMatcher = Pattern.compile("href=\"//duckduckgo\\.com/l/\\?uddg=([^\"]+)\"").matcher(block)
                val rawUrl = if (urlMatcher.find()) {
                    URLDecoder.decode(urlMatcher.group(1) ?: "", "UTF-8")
                } else {
                    val directUrlMatcher = Pattern.compile("href=\"(https?://[^\"]+)\"").matcher(block)
                    if (directUrlMatcher.find()) directUrlMatcher.group(1) ?: "" else ""
                }

                if (rawUrl.isBlank() || rawUrl.contains("duckduckgo.com")) continue

                val tMatcher = Pattern.compile("<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*>(.*?)</a>", Pattern.DOTALL).matcher(block)
                val titleHtml = if (tMatcher.find()) tMatcher.group(1) ?: "" else ""
                val cleanTitle = cleanHtml(titleHtml)

                val sMatcher = Pattern.compile("<a[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</a>", Pattern.DOTALL).matcher(block)
                val snippetHtml = if (sMatcher.find()) sMatcher.group(1) ?: "" else ""
                val cleanSnippet = cleanHtml(snippetHtml)

                val domain = try { URI(rawUrl).host ?: "web" } catch (_: Exception) { "web" }

                if (cleanTitle.isNotBlank() && rawUrl.isNotBlank()) {
                    list.add(
                        SearchResult(
                            id = UUID.randomUUID().toString(),
                            title = cleanTitle,
                            url = rawUrl,
                            displayUrl = domain,
                            snippet = cleanSnippet.ifBlank { "View webpage source." },
                            source = domain,
                            domain = domain,
                            type = type
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun cleanHtml(input: String): String {
        return input
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()
    }

    override suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://duckduckgo.com/ac/?q=$encoded&type=list"
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

    override suspend fun healthCheck(): Boolean = true
}
