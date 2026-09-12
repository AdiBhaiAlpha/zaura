package com.example.core.ai

/**
 * Centralized configuration for OpenRouter AI integration.
 * OpenRouter serves as the single AI gateway for Zaura.
 * Direct vendor APIs (including Google Gemini direct API) are strictly bypassed.
 */
object OpenRouterConfig {

    // Base API Endpoints
    const val BASE_URL = "https://openrouter.ai/api/v1"
    const val CHAT_COMPLETIONS_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    const val MODELS_ENDPOINT = "https://openrouter.ai/api/v1/models"

    // Default primary model (Routed strictly through OpenRouter, not Google direct)
    const val DEFAULT_PRIMARY_MODEL = "google/gemini-2.0-flash-001"

    // Default fallback model (OpenRouter free tier auto-routing)
    const val DEFAULT_FALLBACK_MODEL = "openrouter/free"

    // OpenRouter Attribution Headers
    const val HEADER_HTTP_REFERER = "HTTP-Referer"
    const val DEFAULT_HTTP_REFERER = "https://zaura.browser"
    const val HEADER_X_TITLE = "X-Title"
    const val DEFAULT_X_TITLE = "Zaura Browser"

    // Context & Request Budget limits to maintain speed and low RAM/token usage
    const val MAX_GROUNDING_SOURCES = 5
    const val MAX_SNIPPET_LENGTH = 320
    const val MAX_PAGE_CONTENT_LENGTH = 3500
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 45L
    const val WRITE_TIMEOUT_SECONDS = 15L

    // Curated catalog of available models (including free tier :free variants)
    data class ModelDescriptor(
        val id: String,
        val displayName: String,
        val isFree: Boolean,
        val providerBadge: String,
        val description: String
    )

    val CURATED_MODELS: List<ModelDescriptor> = listOf(
        ModelDescriptor(
            id = "google/gemini-2.0-flash-001",
            displayName = "Gemini 2.0 Flash (via OpenRouter)",
            isFree = false,
            providerBadge = "Fast & Multimodal",
            description = "High-speed reasoning and synthesis routed via OpenRouter gateway."
        ),
        ModelDescriptor(
            id = "openrouter/free",
            displayName = "OpenRouter Free Auto-Router",
            isFree = true,
            providerBadge = "Free Tier",
            description = "Dynamically routes to currently available top free models."
        ),
        ModelDescriptor(
            id = "meta-llama/llama-3.3-70b-instruct:free",
            displayName = "Llama 3.3 70B Instruct (Free)",
            isFree = true,
            providerBadge = "Open Source",
            description = "Meta's flagship 70B model with exceptional reasoning capabilities."
        ),
        ModelDescriptor(
            id = "deepseek/deepseek-r1:free",
            displayName = "DeepSeek R1 (Free)",
            isFree = true,
            providerBadge = "Reasoning",
            description = "Advanced chain-of-thought open reasoning model."
        ),
        ModelDescriptor(
            id = "mistralai/mistral-7b-instruct:free",
            displayName = "Mistral 7B Instruct (Free)",
            isFree = true,
            providerBadge = "Lightweight",
            description = "Ultra-compact, low-latency instruction model."
        )
    )

    /**
     * Centralized Zaura AI System Prompt.
     * Keeps responses concise, research-oriented, and grounded in web citations.
     */
    val SYSTEM_PROMPT = """
        You are Zaura AI, a fast, accurate, and research-oriented browser intelligence assistant.
        The web browser is the primary experience; you provide helpful, grounded synthesis without dominating the interface.
        
        GUIDELINES:
        1. When web sources are provided, strictly ground claims in those sources and cite them using bracketed numbers [1], [2].
        2. Distinguish clearly between facts retrieved from sources and general inferences.
        3. Transparently acknowledge uncertainty or lack of reliable sources. Never fabricate citations.
        4. Keep answers concise, scannable, and direct.
        5. At the very end of your response, suggest 2-3 brief relevant follow-up questions starting with 'Follow-up:'.
    """.trimIndent()
}
