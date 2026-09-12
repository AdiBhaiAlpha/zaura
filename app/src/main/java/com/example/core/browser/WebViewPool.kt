package com.example.core.browser

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Memory-aware WebView pool for low-RAM devices.
 * Limits concurrent alive WebViews (maximum 3 alive).
 * Inactive tabs have their navigation state saved to Bundle/URL and their heavy
 * WebView instances destroyed to prevent memory bloat and background CPU drain.
 */
class WebViewPool(
    private val context: Context,
    private val contentBlocker: ContentBlocker
) {
    companion object {
        private const val MAX_ALIVE_WEBVIEWS = 3
    }

    private val activeWebViews = ConcurrentHashMap<String, WebView>()
    private val savedTabStates = ConcurrentHashMap<String, Bundle>()
    private val savedTabUrls = ConcurrentHashMap<String, String>()
    private val accessOrder = ConcurrentLinkedDeque<String>()

    private val defaultUserAgent: String by lazy {
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        }
    }

    private val desktopUserAgent: String by lazy {
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(
        tabId: String,
        isPrivate: Boolean,
        onProgressChanged: (Int) -> Unit,
        onTitleReceived: (String) -> Unit,
        onPageStarted: (String) -> Unit,
        onPageFinished: (String) -> Unit,
        onUrlChanged: (String) -> Unit
    ): WebView {
        // Update access order for LRU tracking
        updateAccessOrder(tabId)

        // Return existing active instance if available
        activeWebViews[tabId]?.let {
            suspendInactiveTabs(tabId)
            return it
        }

        // Memory management: Prune least recently used inactive tabs if cap exceeded
        pruneInactiveWebViewsIfNeeded(excludeTabId = tabId)

        val webView = WebView(context.applicationContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = false
                allowContentAccess = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }

                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            }

            // Private tab cookie and storage semantics
            if (isPrivate) {
                CookieManager.getInstance().setAcceptCookie(false)
            } else {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }

            webChromeClient = ZauraWebChromeClient(
                onProgressChangedCallback = onProgressChanged,
                onTitleReceivedCallback = onTitleReceived,
                onFaviconReceivedCallback = { /* handled */ }
            )

            webViewClient = ZauraWebViewClient(
                context = context,
                contentBlocker = contentBlocker,
                onPageStartedCallback = onPageStarted,
                onPageFinishedCallback = onPageFinished,
                onUrlChangedCallback = onUrlChanged
            )

            // Restore saved state if this tab was suspended previously
            val savedBundle = savedTabStates.remove(tabId)
            if (savedBundle != null) {
                restoreState(savedBundle)
            } else {
                val fallbackUrl = savedTabUrls.remove(tabId)
                if (!fallbackUrl.isNullOrBlank() && fallbackUrl != "about:blank") {
                    loadUrl(fallbackUrl)
                }
            }
        }

        activeWebViews[tabId] = webView
        suspendInactiveTabs(tabId)
        return webView
    }

    private fun updateAccessOrder(tabId: String) {
        accessOrder.remove(tabId)
        accessOrder.addLast(tabId)
    }

    private fun pruneInactiveWebViewsIfNeeded(excludeTabId: String) {
        while (activeWebViews.size >= MAX_ALIVE_WEBVIEWS) {
            // Find least recently used tab that is not the currently requested tab
            val candidateId = accessOrder.firstOrNull { it != excludeTabId && activeWebViews.containsKey(it) }
                ?: break

            suspendAndDestroyWebView(candidateId)
        }
    }

    /**
     * Saves tab navigation state and releases the WebView instance to conserve memory.
     */
    private fun suspendAndDestroyWebView(tabId: String) {
        val webView = activeWebViews.remove(tabId) ?: return
        try {
            // Save state
            val bundle = Bundle()
            webView.saveState(bundle)
            savedTabStates[tabId] = bundle
            savedTabUrls[tabId] = webView.url ?: ""

            // Safely destroy WebView
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.clearHistory()
            webView.onPause()
            webView.pauseTimers()
            webView.removeAllViews()
            webView.destroy()
        } catch (_: Exception) {}
    }

    fun setDesktopMode(tabId: String, enabled: Boolean) {
        val webView = activeWebViews[tabId] ?: return
        webView.settings.userAgentString = if (enabled) desktopUserAgent else defaultUserAgent
        webView.reload()
    }

    fun destroyWebView(tabId: String) {
        accessOrder.remove(tabId)
        savedTabStates.remove(tabId)
        savedTabUrls.remove(tabId)
        val webView = activeWebViews.remove(tabId) ?: return
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.clearHistory()
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.pauseTimers()
            webView.removeAllViews()
            webView.destroy()
        } catch (_: Exception) {}
    }

    fun suspendInactiveTabs(activeTabId: String) {
        for ((id, webView) in activeWebViews) {
            if (id != activeTabId) {
                try {
                    webView.onPause()
                    webView.pauseTimers()
                } catch (_: Exception) {}
            } else {
                try {
                    webView.onResume()
                    webView.resumeTimers()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Aggressive memory pressure handler for low-RAM Android devices.
     * Suspends all background tabs and frees caches.
     */
    fun onTrimMemory(level: Int, activeTabId: String?) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            // Suspend and release all inactive WebViews to RAM
            for (id in activeWebViews.keys.toList()) {
                if (id != activeTabId) {
                    suspendAndDestroyWebView(id)
                }
            }
        }
    }

    fun clearAllWebViews() {
        accessOrder.clear()
        savedTabStates.clear()
        savedTabUrls.clear()
        for (id in activeWebViews.keys.toList()) {
            destroyWebView(id)
        }
    }
}
