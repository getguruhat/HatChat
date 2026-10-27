package dev.openinbox.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
 * Messenger tab: Facebook's own web inbox in a WebView.
 *
 * Personal Messenger chats have no public API, so this loads facebook.com/messages
 * directly. You sign in on Facebook's real page; the app never sees your password,
 * and the login cookie stays in this app's private storage.
 */
class MessengerWeb(
    context: Context,
    private val pickFiles: (Intent, ValueCallback<Array<Uri>>) -> Unit,
) {
    var canGoBack by mutableStateOf(false); private set

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
                if (uri.scheme == "https" && isMetaHost(uri.host)) return false
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
            userAgentString = if (desktop) DESKTOP_UA else null // null = WebView default (mobile)
            useWideViewPort = desktop
            loadWithOverviewMode = desktop
            builtInZoomControls = desktop
            displayZoomControls = false
        }
        webView.loadUrl(START_URL)
    }

    fun goBack() = webView.goBack()

    companion object {
        const val START_URL = "https://www.facebook.com/messages/"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        private val META_HOSTS = listOf("facebook.com", "messenger.com", "fb.com", "fbcdn.net", "facebook.net")

        fun isMetaHost(host: String?) =
            host != null && META_HOSTS.any { host == it || host.endsWith(".$it") }

        /** Signs out of Messenger by wiping the WebView's cookies and storage. */
        fun clearLogin(web: MessengerWeb) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            web.webView.clearCache(true)
            web.desktopMode = null
        }
    }
}

@Composable
fun MessengerScreen(web: MessengerWeb, desktopMode: Boolean) {
    BackHandler(enabled = web.canGoBack) { web.goBack() }
    AndroidView(
        factory = { (web.webView.parent as? android.view.ViewGroup)?.removeView(web.webView); web.webView },
        update = { web.load(desktopMode) },
        modifier = Modifier.fillMaxSize(),
    )
}
