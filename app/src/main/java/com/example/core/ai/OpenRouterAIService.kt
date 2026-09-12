package com.example.core.ai

import com.example.core.model.SearchResult
import com.example.core.model.SourceCitation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Implementation of AIService backed by OpenRouter through AIProvider abstraction.
 * All queries, summaries, translations, and deep research are routed through
 * OpenRouter, ensuring no direct vendor SDK dependencies exist.
 */
class OpenRouterAIService(
    private val provider: AIProvider,
    private val modelIdProvider: () -> String = { OpenRouterConfig.DEFAULT_PRIMARY_MODEL }
) : AIService {

    override suspend fun generateAnswer(
        query: String,
        contextSources: List<SearchResult>
    ): AIAnswerResult = withContext(Dispatchers.IO) {
        val currentModel = modelIdProvider()
        val request = AIRequest(
            model = currentModel,
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "Please provide a concise, factual answer to: \"$query\""
                )
            ),
            searchGroundingSources = contextSources.take(OpenRouterConfig.MAX_GROUNDING_SOURCES),
            stream = false
        )

        val result = provider.generate(request)
        if (result.isSuccess) {
            val resp = result.getOrThrow()
            AIAnswerResult(
                answer = resp.text,
                citations = resp.citations,
                followups = resp.followups,
                isGrounded = resp.isGrounded,
                modelUsed = resp.modelUsed
            )
        } else {
            fallbackLocalAnswer(query, contextSources)
        }
    }

    override fun streamAnswer(
        query: String,
        contextSources: List<SearchResult>
    ): Flow<AIStreamChunk> {
        val currentModel = modelIdProvider()
        val request = AIRequest(
            model = currentModel,
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "Please provide a concise, factual answer to: \"$query\""
                )
            ),
            searchGroundingSources = contextSources.take(OpenRouterConfig.MAX_GROUNDING_SOURCES),
            stream = true
        )
        return provider.stream(request)
    }

    override suspend fun summarizePage(
        url: String,
        pageTitle: String,
        pageContent: String
    ): String = withContext(Dispatchers.IO) {
        val currentModel = modelIdProvider()
        val safeContent = pageContent.take(OpenRouterConfig.MAX_PAGE_CONTENT_LENGTH)

        val prompt = """
            Summarize the following webpage content clearly and concisely:
            
            PAGE URL: $url
            PAGE TITLE: $pageTitle
            
            PAGE CONTENT:
            $safeContent
            
            FORMAT:
            - Core Takeaway (1 clear sentence)
            - Key Highlights (3-4 bullet points)
        """.trimIndent()

        val request = AIRequest(
            model = currentModel,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            stream = false
        )

        val result = provider.generate(request)
        if (result.isSuccess) {
            result.getOrThrow().text
        } else {
            "Unable to generate AI summary at this time. Please check your network or API settings."
        }
    }

    override fun streamSummarizePage(
        url: String,
        pageTitle: String,
        pageContent: String
    ): Flow<AIStreamChunk> {
        val currentModel = modelIdProvider()
        val safeContent = pageContent.take(OpenRouterConfig.MAX_PAGE_CONTENT_LENGTH)

        val prompt = """
            Summarize the following webpage content clearly and concisely:
            PAGE TITLE: $pageTitle
            $safeContent
            
            Provide a 1-sentence Core Takeaway followed by 3 key bullet points.
        """.trimIndent()

        val request = AIRequest(
            model = currentModel,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            stream = true
        )
        return provider.stream(request)
    }

    override suspend fun askAboutPage(
        url: String,
        pageTitle: String,
        pageContent: String,
        question: String
    ): String = withContext(Dispatchers.IO) {
        val currentModel = modelIdProvider()
        val safeContent = pageContent.take(OpenRouterConfig.MAX_PAGE_CONTENT_LENGTH)

        val prompt = """
            Answer the user's question accurately based on the page content provided below.
            
            PAGE URL: $url
            PAGE TITLE: $pageTitle
            
            PAGE CONTENT:
            $safeContent
            
            USER QUESTION: "$question"
            
            If the answer cannot be found in the content, state so transparently.
        """.trimIndent()

        val request = AIRequest(
            model = currentModel,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            stream = false
        )

        val result = provider.generate(request)
        if (result.isSuccess) {
            result.getOrThrow().text
        } else {
            "Unable to answer from this page at the moment."
        }
    }

    override suspend fun translateText(
        text: String,
        targetLanguage: String
    ): String = withContext(Dispatchers.IO) {
        val currentModel = modelIdProvider()
        val safeText = text.take(3000)

        val prompt = "Translate the following text accurately into $targetLanguage while preserving structure:\n\n$safeText"
        val request = AIRequest(
            model = currentModel,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            stream = false
        )

        val result = provider.generate(request)
        if (result.isSuccess) {
            result.getOrThrow().text
        } else {
            "Translation unavailable at this time."
        }
    }

    override suspend fun compareTabs(
        tabContents: List<TabContentContext>
    ): String = withContext(Dispatchers.IO) {
        val currentModel = modelIdProvider()
        val builder = StringBuilder()
        tabContents.forEachIndexed { idx, tab ->
            builder.append("TAB [${idx + 1}]: ${tab.title} (${tab.url})\n")
            builder.append("CONTENT: ${tab.content.take(1200)}\n\n")
        }

        val prompt = """
            Compare the following open tabs in Zaura Browser:
            
            $builder
            
            Provide a structured comparative summary:
            1. Overview & Common Topic
            2. Key Differences in Perspective
            3. Unique Points from each Source
            4. Synthesis & Recommendation
        """.trimIndent()

        val request = AIRequest(
            model = currentModel,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            stream = false
        )

        val result = provider.generate(request)
        if (result.isSuccess) {
            result.getOrThrow().text
        } else {
            "Comparison failed to synthesize."
        }
    }

    override suspend fun runDeepResearch(
        query: String,
        searchResults: List<SearchResult>
    ): ResearchReport = withContext(Dispatchers.IO) {
        val currentModel = modelIdProvider()
        val sources = searchResults.take(6).mapIndexed { idx, res ->
            SourceCitation(
                id = idx + 1,
                title = res.title,
                url = res.url,
                domain = res.domain,
                snippet = res.snippet.take(OpenRouterConfig.MAX_SNIPPET_LENGTH)
            )
        }

        val contextStr = sources.joinToString("\n\n") { "[${it.id}] ${it.title} (${it.domain}):\n${it.snippet}" }
        val prompt = """
            Conduct a Deep Research Report on: "$query"
            
            GROUNDING SOURCES:
            $contextStr
            
            Format response strictly as JSON with this schema:
            {
              "executiveSummary": "Concise high-level overview with bracketed citations like [1]",
              "keyFindings": ["Finding 1 [1]", "Finding 2 [2]", "Finding 3 [3]"],
              "comprehensiveAnalysis": "Detailed multi-paragraph breakdown analyzing the topic thoroughly with citations.",
              "suggestedQuestions": ["Follow up question 1?", "Follow up question 2?"]
            }
        """.trimIndent()

        val request = AIRequest(
            model = currentModel,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            stream = false
        )

        val result = provider.generate(request)
        if (result.isSuccess) {
            val raw = result.getOrThrow().text
            val cleanJson = extractJsonSubstring(raw)
            try {
                val json = JSONObject(cleanJson)
                val execSummary = json.optString("executiveSummary", "Executive summary generated.")
                val findings = mutableListOf<String>()
                json.optJSONArray("keyFindings")?.let { arr ->
                    for (i in 0 until arr.length()) findings.add(arr.getString(i))
                }
                val analysis = json.optString("comprehensiveAnalysis", "Analysis synthesized.")
                val questions = mutableListOf<String>()
                json.optJSONArray("suggestedQuestions")?.let { arr ->
                    for (i in 0 until arr.length()) questions.add(arr.getString(i))
                }

                ResearchReport(
                    title = "Deep Research: $query",
                    query = query,
                    executiveSummary = execSummary,
                    keyFindings = findings.ifEmpty { listOf("Synthesized overview of $query") },
                    comprehensiveAnalysis = analysis,
                    sourceCitations = sources,
                    suggestedQuestions = questions.ifEmpty { listOf("Explore trends related to $query") }
                )
            } catch (_: Exception) {
                fallbackReport(query, sources)
            }
        } else {
            fallbackReport(query, sources)
        }
    }

    override fun cancelActiveRequests() {
        provider.cancel()
    }

    private fun fallbackLocalAnswer(query: String, contextSources: List<SearchResult>): AIAnswerResult {
        if (contextSources.isEmpty()) {
            return AIAnswerResult(
                answer = "Zaura searched the web for \"$query\". View the live web and news links below.",
                citations = emptyList(),
                followups = listOf("Search latest news on $query", "Find related articles"),
                isGrounded = false,
                modelUsed = "local-fallback"
            )
        }

        val answerText = buildString {
            append("Summary of top web results for \"$query\":\n\n")
            contextSources.take(3).forEachIndexed { idx, res ->
                val num = idx + 1
                append("• [$num] ${res.title}: ${res.snippet}\n\n")
            }
        }

        val citations = contextSources.take(3).mapIndexed { idx, res ->
            SourceCitation(
                id = idx + 1,
                title = res.title,
                url = res.url,
                domain = res.domain,
                snippet = res.snippet
            )
        }

        return AIAnswerResult(
            answer = answerText.trim(),
            citations = citations,
            followups = listOf("Related developments in $query", "Different perspectives on $query"),
            isGrounded = true,
            modelUsed = "web-grounded-summary"
        )
    }

    private fun fallbackReport(query: String, sources: List<SourceCitation>): ResearchReport {
        return ResearchReport(
            title = "Research Dossier: $query",
            query = query,
            executiveSummary = "Synthesis of top available web sources for \"$query\".",
            keyFindings = sources.map { "${it.title}: ${it.snippet.take(120)}" },
            comprehensiveAnalysis = "Grounded multi-source overview compiled from verified web results.",
            sourceCitations = sources,
            suggestedQuestions = listOf("What are related developments in $query?")
        )
    }

    private fun extractJsonSubstring(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            raw.substring(start, end + 1)
        } else {
            raw
        }
    }
}
