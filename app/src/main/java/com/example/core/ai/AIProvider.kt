package com.example.core.ai

import com.example.core.model.SearchResult
import com.example.core.model.SourceCitation
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

data class AIRequest(
    val model: String = OpenRouterConfig.DEFAULT_PRIMARY_MODEL,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.5,
    val maxTokens: Int = 1500,
    val stream: Boolean = false,
    val searchGroundingSources: List<SearchResult> = emptyList(),
    val requestId: String = UUID.randomUUID().toString()
)

data class AIResponse(
    val text: String,
    val modelUsed: String,
    val totalTokens: Int? = null,
    val citations: List<SourceCitation> = emptyList(),
    val followups: List<String> = emptyList(),
    val isGrounded: Boolean = true
)

data class AIStreamChunk(
    val textDelta: String,
    val fullTextSoFar: String = "",
    val modelUsed: String = "",
    val isDone: Boolean = false,
    val error: String? = null,
    val citations: List<SourceCitation> = emptyList()
)

/**
 * Provider abstraction interface for Zaura AI.
 * The browser UI interacts with this abstraction, keeping the underlying
 * gateway (OpenRouter) modular and replaceable without UI alterations.
 */
interface AIProvider {
    val providerName: String

    suspend fun generate(request: AIRequest): Result<AIResponse>

    fun stream(request: AIRequest): Flow<AIStreamChunk>

    fun cancel(requestId: String? = null)
}
