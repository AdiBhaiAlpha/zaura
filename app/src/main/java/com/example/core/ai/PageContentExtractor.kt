package com.example.core.ai

import com.example.core.model.ReaderArticle
import java.util.regex.Pattern

object PageContentExtractor {

    fun extractReadableArticle(html: String, url: String): ReaderArticle {
        if (html.isBlank()) {
            return ReaderArticle(
                title = "Web Page",
                content = "No readable content extracted.",
                url = url
            )
        }

        // 1. Extract Title
        val titleMatcher = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
        val rawTitle = if (titleMatcher.find()) titleMatcher.group(1) ?: "Article" else "Article"
        val cleanTitle = cleanHtmlText(rawTitle)

        // 2. Strip scripts, styles, comments, nav, footer, header, svg, noscript, iframes
        var sanitized = html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<!--[\\s\\S]*?-->"), " ")
            .replace(Regex("<nav[\\s\\S]*?</nav>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<header[\\s\\S]*?</header>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<footer[\\s\\S]*?</footer>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<aside[\\s\\S]*?</aside>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<svg[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<noscript[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<iframe[\\s\\S]*?</iframe>", RegexOption.IGNORE_CASE), " ")

        // 3. Prefer <article> or <main> if present
        val articleMatcher = Pattern.compile("<article[\\s\\S]*?</article>", Pattern.CASE_INSENSITIVE).matcher(sanitized)
        if (articleMatcher.find()) {
            sanitized = articleMatcher.group(0) ?: sanitized
        } else {
            val mainMatcher = Pattern.compile("<main[\\s\\S]*?</main>", Pattern.CASE_INSENSITIVE).matcher(sanitized)
            if (mainMatcher.find()) {
                sanitized = mainMatcher.group(0) ?: sanitized
            }
        }

        // 4. Convert block elements to clean newlines
        sanitized = sanitized
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</li>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "\n\n")

        val cleanText = cleanHtmlText(sanitized)
            .lines()
            .map { it.trim() }
            .filter { it.length > 20 || it.startsWith("#") }
            .joinToString("\n\n")

        val finalContent = if (cleanText.isBlank()) "Could not extract structured text from this page." else cleanText
        val excerpt = finalContent.take(200)

        return ReaderArticle(
            title = cleanTitle,
            content = finalContent,
            excerpt = excerpt,
            url = url,
            textLength = finalContent.length
        )
    }

    fun cleanHtmlText(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
