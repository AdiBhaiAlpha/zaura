package com.example.core.ai

import android.util.Log
import com.example.core.model.SearchResult
import com.example.core.model.SourceCitation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * OpenRouter AI Gateway Provider for Zaura Browser.
 * 
 * Complies strictly with OpenRouter specifications:
 * - Base endpoint: https://openrouter.ai/api/v1
 * - Chat endpoint: https://openrouter.ai/api/v1/chat/completions
 * - OpenAI-compatible chat completion payload
 * - Model fallback (primary -> openrouter/free)
 * - Server-Sent Events (SSE) streaming support
 * - Attributed headers (HTTP-Referer, X-Title)
 * - Transparent error handling without vendor leakage
 */
class OpenRouterProvider(
    private val client: OkHttpClient = defaultHttpClient(),
    private val apiKeyProvider: () -> String? = { null },
    private val proxyUrlProvider: () -> String? = { null },
    private val defaultModelProvider: () -> String = { OpenRouterConfig.DEFAULT_PRIMARY_MODEL },
    private val fallbackModelProvider: () -> String = { OpenRouterConfig.DEFAULT_FALLBACK_MODEL }
) : AIProvider {

    override val providerName: String = "OpenRouter"

    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val lastRequestTimestamp = AtomicLong(0)

    companion object {
        private const val TAG = "ZauraOpenRouter"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(OpenRouterConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(OpenRouterConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(OpenRouterConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build()
        }
    }

    override suspend fun generate(request: AIRequest): Result<AIResponse> = withContext(Dispatchers.IO) {
        val targetModel = if (request.model.isNotBlank()) request.model else defaultModelProvider()
        val fallbackModel = fallbackModelProvider()

        // Enforce light cooldown to prevent rapid multi-tap abuse
        enforceRequestCooldown()

        val startTime = System.currentTimeMillis()
        val result = executeChatCompletion(
            request = request,
            modelToUse = targetModel,
            isStreaming = false
        )

        // Handle model fallback if primary fails with 404, 429, 500, 502, 503, 504
        if (result.isFailure && targetModel != fallbackModel) {
            val err = result.exceptionOrNull()
            Log.w(TAG, "Primary model '$targetModel' failed (${err?.message}). Initiating fallback to '$fallbackModel'")

            val fallbackResult = executeChatCompletion(
                request = request,
                modelToUse = fallbackModel,
                isStreaming = false
            )

            val latency = System.currentTimeMillis() - startTime
            logObservability(request.requestId, fallbackModel, latency, fallbackResult.isSuccess)
            return@withContext fallbackResult
        }

        val latency = System.currentTimeMillis() - startTime
        logObservability(request.requestId, targetModel, latency, result.isSuccess)
        return@withContext result
    }

    override fun stream(request: AIRequest): Flow<AIStreamChunk> = flow {
        val targetModel = if (request.model.isNotBlank()) request.model else defaultModelProvider()
        val fallbackModel = fallbackModelProvider()

        enforceRequestCooldown()

        // Build sources & citations
        val citations = prepareCitations(request.searchGroundingSources)

        // Attempt streaming with target model
        val streamedSuccessfully = runCatching {
            executeStreamingCall(request, targetModel, citations) { chunk ->
                emit(chunk)
            }
        }.isSuccess

        if (!streamedSuccessfully && targetModel != fallbackModel) {
            Log.w(TAG, "Streaming failed on '$targetModel'. Falling back to '$fallbackModel'")
            emit(AIStreamChunk(textDelta = "", fullTextSoFar = "", modelUsed = fallbackModel, isDone = false))

            runCatching {
                executeStreamingCall(request, fallbackModel, citations) { chunk ->
                    emit(chunk)
                }
            }.onFailure { err ->
                val friendlyError = mapToUserFriendlyError(err)
                emit(AIStreamChunk(textDelta = "", fullTextSoFar = "", modelUsed = fallbackModel, isDone = true, error = friendlyError))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel(requestId: String?) {
        if (requestId != null) {
            activeCalls.remove(requestId)?.cancel()
            Log.d(TAG, "Cancelled AI request: $requestId")
        } else {
            activeCalls.values.forEach { it.cancel() }
            activeCalls.clear()
            Log.d(TAG, "Cancelled all active AI requests")
        }
    }

    /**
     * Executes synchronous chat completion for non-streaming queries.
     */
    private fun executeChatCompletion(
        request: AIRequest,
        modelToUse: String,
        isStreaming: Boolean
    ): Result<AIResponse> {
        val proxyUrl = proxyUrlProvider()
        val endpoint = if (!proxyUrl.isNullOrBlank()) proxyUrl else OpenRouterConfig.CHAT_COMPLETIONS_ENDPOINT
        val apiKey = apiKeyProvider()

        val citations = prepareCitations(request.searchGroundingSources)
        val messagesPayload = buildMessagesJsonArray(request.messages, request.searchGroundingSources)

        val jsonBody = JSONObject().apply {
            put("model", modelToUse)
            put("messages", messagesPayload)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            put("stream", isStreaming)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header(OpenRouterConfig.HEADER_HTTP_REFERER, OpenRouterConfig.DEFAULT_HTTP_REFERER)
            .header(OpenRouterConfig.HEADER_X_TITLE, OpenRouterConfig.DEFAULT_X_TITLE)

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val call = client.newCall(requestBuilder.build())
        activeCalls[request.requestId] = call

        return try {
            val response = call.execute()
            val responseCode = response.code

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseCode, response.body?.string())
                return Result.failure(OpenRouterApiException(responseCode, errorMsg))
            }

            val bodyString = response.body?.string() ?: ""
            val jsonResponse = JSONObject(bodyString)

            val choices = jsonResponse.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return Result.failure(Exception("No choices returned from OpenRouter"))
            }

            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message")
            val content = message?.optString("content", "") ?: ""
            val modelReported = jsonResponse.optString("model", modelToUse)

            val usage = jsonResponse.optJSONObject("usage")
            val totalTokens = usage?.optInt("total_tokens")

            val followups = extractFollowups(content)
            val cleanedAnswer = cleanAnswerText(content)

            Result.success(
                AIResponse(
                    text = cleanedAnswer.ifBlank { "Answer synthesized from web context." },
                    modelUsed = modelReported,
                    totalTokens = totalTokens,
                    citations = citations,
                    followups = followups,
                    isGrounded = citations.isNotEmpty()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            activeCalls.remove(request.requestId)
        }
    }

    /**
     * Executes Server-Sent Events (SSE) streaming call.
     */
    private suspend fun executeStreamingCall(
        request: AIRequest,
        modelToUse: String,
        citations: List<SourceCitation>,
        onChunk: suspend (AIStreamChunk) -> Unit
    ) {
        val proxyUrl = proxyUrlProvider()
        val endpoint = if (!proxyUrl.isNullOrBlank()) proxyUrl else OpenRouterConfig.CHAT_COMPLETIONS_ENDPOINT
        val apiKey = apiKeyProvider()

        val messagesPayload = buildMessagesJsonArray(request.messages, request.searchGroundingSources)

        val jsonBody = JSONObject().apply {
            put("model", modelToUse)
            put("messages", messagesPayload)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            put("stream", true)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header(OpenRouterConfig.HEADER_HTTP_REFERER, OpenRouterConfig.DEFAULT_HTTP_REFERER)
            .header(OpenRouterConfig.HEADER_X_TITLE, OpenRouterConfig.DEFAULT_X_TITLE)

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val call = client.newCall(requestBuilder.build())
        activeCalls[request.requestId] = call

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(response.code, response.body?.string())
                throw OpenRouterApiException(response.code, errorMsg)
            }

            val body = response.body ?: throw Exception("Empty response body")
            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            val fullTextBuilder = StringBuilder()

            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("data:")) {
                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        onChunk(
                            AIStreamChunk(
                                textDelta = "",
                                fullTextSoFar = fullTextBuilder.toString(),
                                modelUsed = modelToUse,
                                isDone = true,
                                citations = citations
                            )
                        )
                        break
                    }

                    if (data.isNotBlank()) {
                        try {
                            val chunkJson = JSONObject(data)
                            val choices = chunkJson.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val textDelta = delta?.optString("content", "") ?: ""
                                if (textDelta.isNotEmpty()) {
                                    fullTextBuilder.append(textDelta)
                                    onChunk(
                                        AIStreamChunk(
                                            textDelta = textDelta,
                                            fullTextSoFar = fullTextBuilder.toString(),
                                            modelUsed = modelToUse,
                                            isDone = false,
                                            citations = citations
                                        )
                                    )
                                }
                            }
                        } catch (_: Exception) {
                            // Ignore SSE JSON parse errors on ping/metadata events
                        }
                    }
                }
                line = reader.readLine()
            }
        } finally {
            activeCalls.remove(request.requestId)
        }
    }

    private fun buildMessagesJsonArray(
        messages: List<ChatMessage>,
        groundingSources: List<SearchResult>
    ): JSONArray {
        val array = JSONArray()

        // 1. Centralized System Prompt
        val systemObj = JSONObject().apply {
            put("role", "system")
            put("content", OpenRouterConfig.SYSTEM_PROMPT)
        }
        array.put(systemObj)

        // 2. If grounding sources exist, prepare formatted web context
        if (groundingSources.isNotEmpty()) {
            val contextText = buildString {
                append("WEB SEARCH SOURCES (Ground your answer on these verified results):\n\n")
                groundingSources.take(OpenRouterConfig.MAX_GROUNDING_SOURCES).forEachIndexed { idx, src ->
                    val num = idx + 1
                    val snippet = src.snippet.take(OpenRouterConfig.MAX_SNIPPET_LENGTH)
                    append("[$num] Title: ${src.title}\n")
                    append("    URL: ${src.url}\n")
                    append("    Snippet: $snippet\n\n")
                }
            }
            val contextObj = JSONObject().apply {
                put("role", "system")
                put("content", contextText)
            }
            array.put(contextObj)
        }

        // 3. User & Assistant conversation messages
        messages.forEach { msg ->
            val msgObj = JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            }
            array.put(msgObj)
        }

        return array
    }

    private fun prepareCitations(sources: List<SearchResult>): List<SourceCitation> {
        return sources.take(OpenRouterConfig.MAX_GROUNDING_SOURCES).mapIndexed { idx, res ->
            SourceCitation(
                id = idx + 1,
                title = res.title,
                url = res.url,
                domain = res.domain,
                snippet = res.snippet.take(OpenRouterConfig.MAX_SNIPPET_LENGTH)
            )
        }
    }

    private fun enforceRequestCooldown() {
        val now = System.currentTimeMillis()
        val last = lastRequestTimestamp.get()
        if (now - last < 350) {
            Thread.sleep(350 - (now - last))
        }
        lastRequestTimestamp.set(System.currentTimeMillis())
    }

    private fun parseErrorMessage(code: Int, body: String?): String {
        return when (code) {
            401 -> "Invalid or missing OpenRouter API Key. Please configure in settings."
            402 -> "OpenRouter account balance exceeded. Free models remain available."
            403 -> "Access restricted for this model on OpenRouter."
            429 -> "Zaura AI is temporarily rate-limited. Please wait a moment."
            500, 502, 503, 504 -> "Zaura AI gateway is temporarily unavailable. Try again in a moment."
            else -> "Zaura AI encountered an error (HTTP $code)."
        }
    }

    private fun mapToUserFriendlyError(throwable: Throwable): String {
        return when (throwable) {
            is OpenRouterApiException -> throwable.message ?: "Zaura AI is temporarily unavailable."
            is java.net.SocketTimeoutException -> "Request timed out. Please try again."
            is java.net.UnknownHostException -> "No internet connection for Zaura AI."
            else -> "Zaura AI is temporarily unavailable. Try again."
        }
    }

    private fun cleanAnswerText(content: String): String {
        return content.lines()
            .filterNot { line ->
                val t = line.trim()
                t.startsWith("Follow-up:", ignoreCase = true) ||
                t.startsWith("Followup:", ignoreCase = true) ||
                t.startsWith("Follow up:", ignoreCase = true)
            }
            .joinToString("\n")
            .trim()
    }

    private fun extractFollowups(content: String): List<String> {
        val followups = mutableListOf<String>()
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("Follow-up:", ignoreCase = true) ||
                trimmed.startsWith("Followup:", ignoreCase = true) ||
                trimmed.startsWith("Follow up:", ignoreCase = true)
            ) {
                val q = trimmed.substringAfter(":").trim().removeSurrounding("\"")
                if (q.isNotBlank()) followups.add(q)
            }
        }
        return if (followups.isEmpty()) {
            listOf("Explore related web results", "Tell me more about this topic")
        } else {
            followups.take(3)
        }
    }

    private fun logObservability(requestId: String, model: String, latencyMs: Long, success: Boolean) {
        // Privacy-safe structured logging: no API keys, no user prompts
        Log.i(
            TAG,
            "[Observability] reqId=$requestId model=$model latency=${latencyMs}ms success=$success"
        )
    }
}

class OpenRouterApiException(val statusCode: Int, message: String) : Exception(message)
