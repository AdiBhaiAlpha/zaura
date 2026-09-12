package com.example.core.search

import com.example.core.model.ClassifiedQuery
import com.example.core.model.QueryIntentType
import java.util.regex.Pattern

object QueryClassifier {
    private val URL_PATTERN = Pattern.compile(
        "^(https?://)?" + // protocol
        "(([a-zA-Z0-9_-]+\\.)+[a-zA-Z]{2,63})" + // domain name
        "(:[0-9]{1,5})?" + // port
        "(/.*)?$" // path
    )

    private val KNOWN_TLDS = setOf(
        "com", "org", "net", "edu", "gov", "io", "ai", "co", "app", "dev",
        "info", "me", "xyz", "tech", "online", "store", "bd", "uk", "in", "de", "ca"
    )

    private val QUESTION_WORDS = setOf(
        "what", "why", "how", "when", "where", "who", "which", "is", "are", "can", "should", "could", "explain", "compare", "summarize", "কী", "কেন", "কীভাবে", "কেমন", "কোথায়"
    )

    fun classify(input: String): ClassifiedQuery {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ClassifiedQuery("", "", QueryIntentType.UNKNOWN)
        }

        // 1. Explicit AI Commands (e.g., "/ai ...", "ask ...", "summarize ...")
        if (trimmed.startsWith("/ai ", ignoreCase = true) ||
            trimmed.startsWith("ai:", ignoreCase = true) ||
            trimmed.startsWith("ask:", ignoreCase = true)
        ) {
            val commandContent = trimmed.substringAfter(" ").trim()
            return ClassifiedQuery(trimmed, commandContent, QueryIntentType.AI_COMMAND)
        }

        // 2. Direct scheme URLs (http://, https://, file://, about:, zaura://)
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("zaura://", ignoreCase = true)
        ) {
            return ClassifiedQuery(trimmed, trimmed, QueryIntentType.URL)
        }

        // 3. Domain-like inputs without spaces (e.g. "github.com", "wikipedia.org/wiki/Kotlin")
        if (!trimmed.contains(" ") && trimmed.contains(".")) {
            val parts = trimmed.split("/")
            val domainPart = parts[0].split(":")[0]
            val tld = domainPart.substringAfterLast(".", "").lowercase()
            
            if (KNOWN_TLDS.contains(tld) || URL_PATTERN.matcher(trimmed).matches()) {
                val normalizedUrl = "https://$trimmed"
                return ClassifiedQuery(trimmed, normalizedUrl, QueryIntentType.URL)
            }
        }

        // 4. Questions (e.g. "What is quantum computing?", "Explain Bangladesh economy")
        val firstWord = trimmed.split(" ")[0].lowercase()
        if (trimmed.endsWith("?") || QUESTION_WORDS.contains(firstWord)) {
            return ClassifiedQuery(trimmed, trimmed, QueryIntentType.QUESTION)
        }

        // 5. Default to standard Web Search
        return ClassifiedQuery(trimmed, trimmed, QueryIntentType.SEARCH)
    }
}
