package com.example.core.browser

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

class ZauraWebChromeClient(
    private val onProgressChangedCallback: (Int) -> Unit,
    private val onTitleReceivedCallback: (String) -> Unit,
    private val onFaviconReceivedCallback: (Bitmap?) -> Unit,
    private val onShowFileChooserCallback: ((ValueCallback<Array<Uri>>?, com.example.core.browser.FileChooserParams) -> Boolean)? = null,
    private val onCustomViewRequested: ((View?, WebChromeClient.CustomViewCallback?) -> Unit)? = null,
    private val onHideCustomViewRequested: (() -> Unit)? = null,
    private val onPermissionRequested: ((PermissionRequest) -> Unit)? = null,
    private val onGeolocationRequested: ((String, GeolocationPermissions.Callback) -> Unit)? = null
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChangedCallback(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        if (!title.isNullOrBlank()) {
            onTitleReceivedCallback(title)
        }
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        onFaviconReceivedCallback(icon)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean {
        if (onShowFileChooserCallback != null && fileChooserParams != null) {
            val params = com.example.core.browser.FileChooserParams(
                filePathCallback = filePathCallback,
                acceptTypes = fileChooserParams.acceptTypes,
                isCaptureEnabled = fileChooserParams.isCaptureEnabled
            )
            return onShowFileChooserCallback.invoke(filePathCallback, params)
        }
        return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (onCustomViewRequested != null) {
            onCustomViewRequested.invoke(view, callback)
        } else {
            super.onShowCustomView(view, callback)
        }
    }

    override fun onHideCustomView() {
        if (onHideCustomViewRequested != null) {
            onHideCustomViewRequested.invoke()
        } else {
            super.onHideCustomView()
        }
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        if (request != null && onPermissionRequested != null) {
            onPermissionRequested.invoke(request)
        } else {
            request?.deny()
        }
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (origin != null && callback != null && onGeolocationRequested != null) {
            onGeolocationRequested.invoke(origin, callback)
        } else {
            callback?.invoke(origin, false, false)
        }
    }
}
