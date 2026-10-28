package com.getguruhat.hatchat.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
    /** If set, only these paths may load; anything else (feed, Watch, Marketplace…) goes back to [startUrl]. */
    private val allowedPaths: List<Regex>? = null,
    /** Extra script run on every page/navigation, e.g. to hide buttons that lead out of the inbox. */
    private val pageScript: String? = null,
    /** Chat pages where the phone's back button should use the site's own Back arrow (see [THREAD_BACK_JS]). */
    private val threadPaths: Regex? = null,
    private val pickFiles: (Intent, ValueCallback<Array<Uri>>) -> Unit,
) {
    var canGoBack by mutableStateOf(false); private set
    var inThread by mutableStateOf(false); private set

    // Facebook opens straight into the most recent chat; go back to the list once if that
    // happens within a few seconds of loading (later chats are ones you opened yourself).
    private var openListUntil = 0L

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
        // In dark mode, let WebView darken pages that have no dark style of their own.
        if (Build.VERSION.SDK_INT >= 33) settings.isAlgorithmicDarkeningAllowed = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && isOwnHost(uri.host)) {
                    // Only redirect whole-page loads; embedded frames (login helpers etc.) load normally.
                    if (request.isForMainFrame && isBlocked(uri) && backToInbox(view)) return true
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
                val path = url?.let { Uri.parse(it).path }.orEmpty()
                inThread = threadPaths?.matches(path) == true
                if (inThread && System.currentTimeMillis() < openListUntil) {
                    openListUntil = 0L
                    view.evaluateJavascript(START_ON_LIST_JS, null)
                }
                Log.d(TAG, "history ${short(url)}")
                // In-page navigation (pushState) never reaches shouldOverrideUrlLoading.
                if (url != null && isBlocked(Uri.parse(url))) {
                    Log.d(TAG, "blocked ${short(url)}")
                    if (backToInbox(view)) return
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
        if (threadPaths != null) openListUntil = System.currentTimeMillis() + 8_000
        webView.loadUrl(startUrl)
    }

    /** Back inside a chat taps the site's own Back arrow; otherwise normal web history. */
    fun goBack() {
        if (!inThread) {
            webView.goBack()
            return
        }
        webView.evaluateJavascript(THREAD_BACK_JS) { clicked ->
            if (clicked != "true" && webView.canGoBack()) webView.goBack()
        }
    }

    /**
     * Desktop sites assume a ~1000px-wide screen, so on a phone they render tiny.
     * Giving the page a phone-width viewport makes Facebook's responsive desktop
     * layout reflow to the screen instead (tested: 980px -> ~455px wide).
     */
    private fun runPageScripts(view: WebView) {
        if (desktopMode == true) view.evaluateJavascript(FIT_VIEWPORT_JS, null)
        pageScript?.let { view.evaluateJavascript(it, null) }
    }

    private val redirects = ArrayDeque<Long>()

    /** Returns to the inbox, unless we've already bounced 3 times in 10 s (the site is redirecting back). */
    private fun backToInbox(view: WebView): Boolean {
        val now = System.currentTimeMillis()
        while (redirects.isNotEmpty() && now - redirects.first() > 10_000) redirects.removeFirst()
        if (redirects.size >= 3) {
            Log.w(TAG, "redirect loop, leaving page as is")
            return false
        }
        redirects.addLast(now)
        view.loadUrl(startUrl)
        return true
    }

    private fun isBlocked(uri: Uri): Boolean {
        val path = uri.path?.ifEmpty { "/" } ?: "/"
        return blockedPaths.any { it.matches(path) } || allowedPaths?.none { it.matches(path) } == true
    }

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
        // On the inbox screens: hide Instagram's top-left Back arrow (it leads to the feed;
        // inside a chat it's kept, since there it returns to the inbox) and the Notes row.
        private const val INSTAGRAM_JS = """(function () {
  function apply() {
    var inbox = /^\/direct\/(inbox(\/general)?|requests)\/?$/.test(location.pathname);
    document.querySelectorAll('svg[aria-label="Back"]').forEach(function (svg) {
      var button = svg.closest('[role=button],a,button') || svg;
      button.style.visibility = inbox ? 'hidden' : '';
      button.style.pointerEvents = inbox ? 'none' : '';
    });
    // Notes row: the horizontal list holding "Your note". Hide its outermost wrapper
    // that still contains only the row (its parent also holds the chat list).
    var labels = document.querySelectorAll('span');
    for (var i = 0; i < labels.length; i++) {
      if (labels[i].childElementCount || labels[i].textContent.trim() !== 'Your note') continue;
      var row = labels[i].closest('ul');
      if (!row) break;
      while (row.parentElement && row.parentElement.childElementCount === 1) row = row.parentElement;
      row.style.display = inbox ? 'none' : '';
      break;
    }
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
        private const val THREAD_BACK_JS = """(function () {
  var b = document.querySelector('[role=button][aria-label="Back"]');
  if (b) { b.click(); return true; }
  return false;
})()"""
        // Same as THREAD_BACK_JS, but waits up to 5 s for the chat header to render.
        private const val START_ON_LIST_JS = """(function () {
  var tries = 0;
  (function attempt() {
    var b = document.querySelector('[role=button][aria-label="Back"]');
    if (b) b.click(); else if (++tries < 25) setTimeout(attempt, 200);
  })();
})()"""
        // Fits Facebook's desktop Messenger to a phone:
        // - hides the top bar (logo, search, menu, notifications, profile), which only leads out;
        // - on /messages/ shows the chat list full screen (Facebook collapses it to zero height
        //   at phone width, leaving an empty "No chats selected" pane);
        // - removes the "screen height minus 56px top bar" limits that left a blank strip,
        //   and the 2px card insets that showed as gray lines on the sides.
        private const val FACEBOOK_JS = """(function () {
  var css = 'div[role=banner]{display:none!important}' +
    ':root{--header-height:0px!important}' +
    'div:has(> div > [role=main]){height:100vh!important;max-height:none!important}' +
    'div:has(> [role=main]){height:100%!important;max-height:none!important;padding:0!important}' +
    '[role=main] > div{padding-top:0!important}' +
    'html.hatchat-inbox div:has(> [aria-label="Thread list"]){height:100vh!important;max-height:none!important}' +
    'html.hatchat-inbox [aria-label="Thread list"]{visibility:visible!important;position:absolute!important;inset:0!important;' +
    'width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;z-index:5!important;' +
    'background:var(--web-wash,#fff)!important}';
  var s = document.getElementById('hatchat-fb');
  if (!s) { s = document.createElement('style'); s.id = 'hatchat-fb'; (document.head || document.documentElement).appendChild(s); }
  if (s.textContent !== css) s.textContent = css;

  function fill() {
    document.documentElement.classList.toggle('hatchat-inbox', /^\/messages\/?$/.test(location.pathname));
    var main = document.querySelector('[role=main]');
    if (!main) return;
    var target = window.innerHeight - 56;
    var nodes = [].slice.call(main.querySelectorAll('div')).concat(
      [].slice.call(document.querySelectorAll('[aria-label="Thread list"], [aria-label="Thread list"] div')));
    for (var i = 0; i < nodes.length; i++) {
      var cs = getComputedStyle(nodes[i]);
      var max = parseFloat(cs.maxHeight);
      if (max && Math.abs(max - target) <= 6) nodes[i].style.setProperty('max-height', 'none', 'important');
      // The chat "card" sits 2px in with rounded corners, so the gray page shows as side lines.
      if (cs.marginLeft === '2px' && nodes[i].getBoundingClientRect().width >= window.innerWidth - 8) {
        nodes[i].style.setProperty('margin', '0', 'important');
        nodes[i].style.setProperty('border-radius', '0', 'important');
      }
    }
  }
  fill();
  if (!window.__hatchatFb) {
    window.__hatchatFb = true;
    var last = 0, timer = null;
    var run = function () {
      var now = Date.now();
      if (now - last > 400) { last = now; fill(); }
      else if (!timer) timer = setTimeout(function () { timer = null; last = Date.now(); fill(); }, 400);
    };
    new MutationObserver(run).observe(document.body || document.documentElement, { childList: true, subtree: true });
    window.addEventListener('resize', run);
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
            pageScript = INSTAGRAM_JS,
            pickFiles = pickFiles,
        )

        fun messenger(context: Context, pickFiles: (Intent, ValueCallback<Array<Uri>>) -> Unit) = WebInbox(
            context,
            startUrl = "https://www.facebook.com/messages/",
            hosts = listOf("facebook.com", "messenger.com", "fb.com", "fbcdn.net", "facebook.net"),
            signOutHosts = listOf("facebook.com", "messenger.com"),
            // Facebook has endless sections, so allow only chats plus the login/security
            // pages it needs; the feed and everything else returns to the inbox.
            allowedPaths = listOf(
                "/messages(/.*)?", "/messenger_media(/.*)?",
                "/login(\\.php)?(/.*)?", "/logout(\\.php)?", "/checkpoint(/.*)?", "/two_step_verification(/.*)?",
                "/two_factor(/.*)?", "/recover(/.*)?", "/auth_platform(/.*)?", "/dialog(/.*)?",
                "/privacy/consent(/.*)?", "/cookie(/.*)?", "/confirmemail(\\.php)?", "/device-based(/.*)?",
            ).map(::Regex),
            pageScript = FACEBOOK_JS,
            threadPaths = Regex("/messages(/e2ee)?/t/.*"),
            pickFiles = pickFiles,
        )
    }
}

@Composable
fun WebInboxScreen(web: WebInbox, desktopMode: Boolean = false) {
    BackHandler(enabled = web.canGoBack || web.inThread) { web.goBack() }
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
