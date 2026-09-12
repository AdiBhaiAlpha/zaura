package com.example.core.ai

import com.example.core.model.SearchResult
import com.example.core.model.SourceCitation
import kotlinx.coroutines.flow.Flow

data class AIAnswerResult(
    val answer: String,
    val citations: List<SourceCitation> = emptyList(),
    val followups: List<String> = emptyList(),
    val isGrounded: Boolean = true,
    val modelUsed: String = ""
)

data class TabContentContext(
    val tabId: String,
    val title: String,
    val url: String,
    val content: String
)

data class ResearchReport(
    val title: String,
    val query: String,
    val executiveSummary: String,
    val keyFindings: List<String>,
    val comprehensiveAnalysis: String,
    val sourceCitations: List<SourceCitation>,
    val suggestedQuestions: List<String>
)

interface AIService {
    suspend fun generateAnswer(query: String, contextSources: List<SearchResult>): AIAnswerResult
    fun streamAnswer(query: String, contextSources: List<SearchResult>): Flow<AIStreamChunk>
    suspend fun summarizePage(url: String, pageTitle: String, pageContent: String): String
    fun streamSummarizePage(url: String, pageTitle: String, pageContent: String): Flow<AIStreamChunk>
    suspend fun askAboutPage(url: String, pageTitle: String, pageContent: String, question: String): String
    suspend fun translateText(text: String, targetLanguage: String): String
    suspend fun compareTabs(tabContents: List<TabContentContext>): String
    suspend fun runDeepResearch(query: String, searchResults: List<SearchResult>): ResearchReport
    fun cancelActiveRequests()
}
