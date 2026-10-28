package com.getguruhat.hatchat.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
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
    /** Paths that leave the inbox (feed, stories…); visiting one sends you back to [startUrl]. */
    private val blockedPaths: List<Regex> = emptyList(),
    /** Extra script run on every page/navigation, e.g. to hide buttons that lead out of the inbox. */
    private val pageScript: String? = null,
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
                if (uri.scheme == "https" && isOwnHost(uri.host)) {
                    if (isBlocked(uri)) { view.loadUrl(startUrl); return true }
                    return false
                }
                // Outside links open in the real browser; app deep links (fb://, intent://) are dropped.
                if (uri.scheme == "http" || uri.scheme == "https") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }
                return true
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack = view.canGoBack()
                Log.d(TAG, "history ${short(url)}")
                // In-page navigation (pushState) never reaches shouldOverrideUrlLoading.
                if (url != null && isBlocked(Uri.parse(url))) {
                    Log.d(TAG, "blocked ${short(url)}, back to inbox")
                    view.loadUrl(startUrl)
                    return
                }
                runPageScripts(view)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                Log.d(TAG, "page start ${short(url)}")
            }

            override fun onPageFinished(view: WebView, url: String?) {
                Log.d(TAG, "page done ${short(url)}")
                runPageScripts(view)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) Log.w(TAG, "load error ${error.errorCode} ${error.description} ${short(request.url.toString())}")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) Log.w(TAG, "http ${response.statusCode} ${short(request.url.toString())}")
            }

            // Without this, a crashed page renderer takes the whole app down.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.e(TAG, "renderer gone, crashed=${detail.didCrash()}")
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.w(TAG, "console: ${message.message().take(200)}")
                }
                return true
            }

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

    /**
     * Desktop sites assume a ~1000px-wide screen, so on a phone they render tiny.
     * Giving the page a phone-width viewport makes Facebook's responsive desktop
     * layout reflow to the screen instead (tested: 980px -> ~455px wide).
     */
    private fun runPageScripts(view: WebView) {
        if (desktopMode == true) view.evaluateJavascript(FIT_VIEWPORT_JS, null)
        pageScript?.let { view.evaluateJavascript(it, null) }
    }

    private fun isBlocked(uri: Uri) = blockedPaths.any { it.matches(uri.path ?: "/") }

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

    /** Host and path only: query strings can carry session details. */
    private fun short(url: String?) = url?.let { Uri.parse(it) }?.let { "${it.host}${it.path}" } ?: "null"

    private fun isOwnHost(host: String?) =
        host != null && hosts.any { host == it || host.endsWith(".$it") }

    companion object {
        private const val TAG = "HatChat"
        // Adds/overrides the viewport tag and keeps it there if the page swaps <head> nodes.
        private const val FIT_VIEWPORT_JS = """(function () {
  var want = 'width=device-width, initial-scale=1';
  function fit() {
    var m = document.querySelector('meta[name=viewport]');
    if (!m) { m = document.createElement('meta'); m.name = 'viewport'; (document.head || document.documentElement).appendChild(m); }
    if (m.content !== want) m.content = want;
  }
  fit();
  if (!window.__hatchatFit) {
    window.__hatchatFit = true;
    new MutationObserver(fit).observe(document.head || document.documentElement, { childList: true });
  }
})();"""
        // On the inbox screens, Instagram's top-left Back arrow leads to the feed: hide it.
        // Inside a chat it's kept, since there it returns to the inbox.
        private const val INSTAGRAM_HIDE_BACK_JS = """(function () {
  function apply() {
    var inbox = /^\/direct\/(inbox(\/general)?|requests)\/?$/.test(location.pathname);
    document.querySelectorAll('svg[aria-label="Back"]').forEach(function (svg) {
      var button = svg.closest('[role=button],a,button') || svg;
      button.style.visibility = inbox ? 'hidden' : '';
      button.style.pointerEvents = inbox ? 'none' : '';
    });
  }
  apply();
  if (!window.__hatchatBack) {
    window.__hatchatBack = true;
    var queued = false;
    new MutationObserver(function () {
      if (queued) return;
      queued = true;
      requestAnimationFrame(function () { queued = false; apply(); });
    }).observe(document.documentElement, { childList: true, subtree: true });
  }
})();"""
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        fun instagram(context: Context, pickFiles: (Intent, ValueCallback<Array<Uri>>) -> Unit) = WebInbox(
            context,
            startUrl = "https://www.instagram.com/direct/inbox/",
            // facebook.com is allowed so "Log in with Facebook" works.
            hosts = listOf("instagram.com", "cdninstagram.com", "facebook.com", "fbcdn.net"),
            signOutHosts = listOf("instagram.com"),
            // Home feed, Explore, the Reels feed and Stories. Single posts and reels
            // that friends send in a chat (/p/…, /reel/…) still open.
            blockedPaths = listOf(Regex("/?"), Regex("/explore(/.*)?"), Regex("/reels/?"), Regex("/stories(/.*)?")),
            pageScript = INSTAGRAM_HIDE_BACK_JS,
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
        factory = {
            (web.webView.parent as? ViewGroup)?.removeView(web.webView)
            // AndroidView defaults to WRAP_CONTENT, which makes CSS viewport units (100vh)
            // resolve to 0 — Instagram's page then collapses to nothing after loading.
            web.webView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            web.webView
        },
        update = { web.load(desktopMode) },
        modifier = Modifier.fillMaxSize(),
    )
}
