package com.example.core.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.*
import androidx.annotation.RequiresApi
import java.io.ByteArrayInputStream

class ZauraWebViewClient(
    private val context: Context,
    private val contentBlocker: ContentBlocker,
    private val onPageStartedCallback: (String) -> Unit,
    private val onPageFinishedCallback: (String) -> Unit,
    private val onUrlChangedCallback: (String) -> Unit,
    private val onSslErrorOccurred: ((SslError, SslErrorHandler) -> Unit)? = null,
    private val onSafeBrowsingThreat: ((SafeBrowsingResponse?) -> Unit)? = null
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val url = uri.toString()

        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https" || scheme == "about") {
            return false // Let WebView load standard web protocols
        }

        // Handle custom intent, mailto, tel, and app deep links securely
        try {
            if (scheme == "mailto" || scheme == "tel") {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            }

            if (scheme == "intent") {
                val parsedIntent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (context.packageManager.resolveActivity(parsedIntent, 0) != null) {
                    context.startActivity(parsedIntent)
                    return true
                }
            }
        } catch (_: Exception) {
            // Silently prevent unsupported custom schemes from crashing the app
            return true
        }

        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val requestUrl = request?.url?.toString() ?: return null

        // Tracker and privacy protection filter
        if (contentBlocker.isUrlBlocked(requestUrl)) {
            val emptyStream = ByteArrayInputStream("".toByteArray())
            return WebResourceResponse("text/plain", "UTF-8", emptyStream)
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (!url.isNullOrBlank()) {
            onPageStartedCallback(url)
            onUrlChangedCallback(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (!url.isNullOrBlank()) {
            onPageFinishedCallback(url)
            onUrlChangedCallback(url)
        }
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        // Strict security: Do not blindly proceed on SSL errors
        if (error != null && handler != null && onSslErrorOccurred != null) {
            onSslErrorOccurred.invoke(error, handler)
        } else {
            handler?.cancel()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onSafeBrowsingHit(
        view: WebView?,
        request: WebResourceRequest?,
        threatType: Int,
        callback: SafeBrowsingResponse?
    ) {
        if (onSafeBrowsingThreat != null) {
            onSafeBrowsingThreat.invoke(callback)
        } else {
            // Default safe browsing behavior: show interstitial warning
            callback?.showInterstitial(true)
        }
    }
}
