package com.example.core.browser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ValueCallback
import kotlinx.coroutines.flow.StateFlow

interface BrowserEngine {
    val currentUrl: StateFlow<String>
    val currentTitle: StateFlow<String>
    val progress: StateFlow<Int>
    val isLoading: StateFlow<Boolean>
    val canGoBack: StateFlow<Boolean>
    val canGoForward: StateFlow<Boolean>
    val isSecure: StateFlow<Boolean>

    fun loadUrl(url: String)
    fun goBack(): Boolean
    fun goForward(): Boolean
    fun reload()
    fun stopLoading()
    fun evaluateJavascript(script: String, resultCallback: ((String) -> Unit)? = null)
    fun setDesktopMode(enabled: Boolean)
    fun destroy()
}

data class WebNavigationEvent(
    val url: String,
    val title: String = "",
    val favicon: Bitmap? = null,
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val isSecure: Boolean = true,
    val error: String? = null
)

data class FileChooserParams(
    val filePathCallback: ValueCallback<Array<Uri>>?,
    val acceptTypes: Array<String>?,
    val isCaptureEnabled: Boolean
)
