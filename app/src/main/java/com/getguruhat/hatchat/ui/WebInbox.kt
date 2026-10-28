package com.getguruhat.hatchat.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A Meta web inbox (Instagram DMs or Messenger) in a WebView.
 *
 * Personal chats have no usable public API, so this loads the service's own website.
 * You sign in on the real login page; the app never sees your password, and the
 * login cookie stays in this app's private storage.
 */
class WebInbox(
    context: Context,
    private val startUrl: String,
    private val hosts: List<String>,
    private val signOutHosts: List<String>,
    private val pickFiles: (Intent, ValueCallback<Array<Uri>>) -> Unit,
) {
    var canGoBack by mutableStateOf(false); private set

    // The WebView user agent marks itself as an in-app browser ("; wv"), which Meta
    // sometimes refuses to log in. Present as regular mobile Chrome instead.
    private val mobileUa = WebSettings.getDefaultUserAgent(context)
        .replace("; wv)", ")")
        .replace(Regex("Version/\\S+ "), "")

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && isOwnHost(uri.host)) return false
                // Outside links open in the real browser; app deep links (fb://, intent://) are dropped.
                if (uri.scheme == "http" || uri.scheme == "https") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }
                return true
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack = view.canGoBack()
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                pickFiles(params.createIntent(), callback)
                return true
            }
        }
    }

    private var desktopMode: Boolean? = null

    fun load(desktop: Boolean) {
        if (desktopMode == desktop) return
        desktopMode = desktop
        webView.settings.apply {
            userAgentString = if (desktop) DESKTOP_UA else mobileUa
            useWideViewPort = desktop
            loadWithOverviewMode = desktop
            builtInZoomControls = desktop
            displayZoomControls = false
        }
        webView.loadUrl(startUrl)
    }

    fun goBack() = webView.goBack()

    /** Signs out by expiring this service's cookies; the other tab stays signed in. */
    fun signOut() {
        val cookies = CookieManager.getInstance()
        for (host in signOutHosts) {
            val url = "https://www.$host/"
            cookies.getCookie(url)?.split(";")?.forEach { pair ->
                val name = pair.substringBefore("=").trim()
                if (name.isNotEmpty()) {
                    cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.$host")
                    cookies.setCookie(url, "$name=; Max-Age=0; Path=/")
                }
            }
        }
        cookies.flush()
        webView.clearCache(true)
        desktopMode = null
    }

    private fun isOwnHost(host: String?) =
        host != null && hosts.any { host == it || host.endsWith(".$it") }

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        fun instagram(context: Context, pickFiles: (Intent, ValueCallback<Array<Uri>>) -> Unit) = WebInbox(
            context,
            startUrl = "https://www.instagram.com/direct/inbox/",
            // facebook.com is allowed so "Log in with Facebook" works.
            hosts = listOf("instagram.com", "cdninstagram.com", "facebook.com", "fbcdn.net"),
            signOutHosts = listOf("instagram.com"),
            pickFiles = pickFiles,
        )

        fun messenger(context: Context, pickFiles: (Intent, ValueCallback<Array<Uri>>) -> Unit) = WebInbox(
            context,
            startUrl = "https://www.facebook.com/messages/",
            hosts = listOf("facebook.com", "messenger.com", "fb.com", "fbcdn.net", "facebook.net"),
            signOutHosts = listOf("facebook.com", "messenger.com"),
            pickFiles = pickFiles,
        )
    }
}

@Composable
fun WebInboxScreen(web: WebInbox, desktopMode: Boolean = false) {
    BackHandler(enabled = web.canGoBack) { web.goBack() }
    AndroidView(
        factory = { (web.webView.parent as? android.view.ViewGroup)?.removeView(web.webView); web.webView },
        update = { web.load(desktopMode) },
        modifier = Modifier.fillMaxSize(),
    )
}
