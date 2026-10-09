package com.comixreader.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.comixreader.app.network.Site
import com.comixreader.app.network.WebViewSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class PageRunResult(val payload: String, val keys: CipherKeys?)

class PageScriptException(message: String) : Exception(message)

/**
 * Runs a comix.to page in an invisible WebView so the site's own JavaScript
 * does the work that native code cannot (signing, decrypting), and hands the
 * result back. While the page boots, every `atob()` result that looks like
 * cipher material is recorded, which is how [CipherKeys] are harvested.
 */
class PageRunner(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val main = Handler(Looper.getMainLooper())

    /**
     * @param pageUrl URL the HTML belongs to (used as base URL).
     * @param html page HTML already fetched through OkHttp.
     * @param watcher JS evaluated every [POLL_MS] ms. It calls `__cx.post(string)`
     *   with the result or `__cx.fail(message)`. It may set `__cx.match` early
     *   (in [bootstrap]) to test every object the page parses from JSON.
     * @param bootstrap JS that runs before any page script.
     */
    suspend fun run(
        pageUrl: String,
        html: String,
        watcher: String,
        bootstrap: String = "",
        timeoutMs: Long = 45_000,
    ): PageRunResult = withContext(Dispatchers.Main) {
        withTimeoutOrNull(timeoutMs) { runOnMain(pageUrl, html, watcher, bootstrap) }
            ?: throw PageScriptException("comix.to took too long to respond. Try again.")
    }

    @SuppressLint("JavascriptInterface", "AddJavascriptInterface")
    private suspend fun runOnMain(
        pageUrl: String,
        html: String,
        watcher: String,
        bootstrap: String,
    ): PageRunResult = suspendCancellableCoroutine { cont ->
        val bridge = "cx" + (1..12).map { ('a'..'z').random() }.joinToString("") + Random.nextInt(1000)
        val webView = WebView(context)
        WebViewSetup.configure(webView)
        webView.settings.blockNetworkImage = true
        var finished = false
        lateinit var poll: Runnable

        fun finish(block: () -> Unit) {
            if (finished) return
            finished = true
            main.removeCallbacks(poll)
            block()
            main.post {
                webView.stopLoading()
                webView.destroy()
            }
        }

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun post(message: String) = main.post {
                    finish {
                        val result = runCatching { parseMessage(message) }
                        result.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
                    }
                }

                @JavascriptInterface
                fun fail(message: String) = main.post {
                    finish { cont.resumeWithException(PageScriptException(message)) }
                }
            },
            bridge,
        )

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val host = request.url.host.orEmpty()
                // Only the site itself and Cloudflare; ads and trackers get nothing.
                return if (Site.isSiteHost(host) || Site.isChallengeHost(host)) null else emptyResponse()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(watcher, null)
            }
        }

        poll = Runnable {
            if (!finished) {
                webView.evaluateJavascript(watcher, null)
                main.postDelayed(poll, POLL_MS)
            }
        }

        val prelude = prelude(bridge) + "\n" + bootstrap
        webView.loadDataWithBaseURL(pageUrl, inject(html, prelude), "text/html", "utf-8", pageUrl)
        main.postDelayed(poll, POLL_MS)

        cont.invokeOnCancellation { main.post { finish {} } }
    }

    private fun parseMessage(message: String): PageRunResult {
        val obj = json.parseToJsonElement(message) as JsonObject
        val keys = obj["keys"]?.takeIf { it is JsonObject }
            ?.let { runCatching { json.decodeFromJsonElement<CipherKeys>(it) }.getOrNull() }
            ?.takeIf { it.isUsable }
        return PageRunResult(obj["payload"]!!.jsonPrimitive.content, keys)
    }

    private fun emptyResponse() = WebResourceResponse("text/plain", "utf-8", "".byteInputStream())

    private fun inject(html: String, script: String): String {
        val tag = "<script>$script</script>"
        val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        return if (head != null) html.replaceRange(head.range.last + 1, head.range.last + 1, tag) else tag + html
    }

    /**
     * Defines `window.__cx`: post/fail to Kotlin, the atob recorder, and
     * hooks that offer every parsed JSON value to `__cx.match` if set.
     */
    private fun prelude(bridge: String) = """
        (function () {
          if (window.__cx) return;
          var cx = window.__cx = { decoded: [], match: null, done: false };
          var nativeAtob = window.atob.bind(window);
          window.atob = function (s) {
            var out = nativeAtob(s);
            try {
              if (out.length === 256 || out.length === 24 || out.length === 32) {
                var bytes = new Array(out.length);
                for (var i = 0; i < out.length; i++) bytes[i] = out.charCodeAt(i) & 255;
                cx.decoded.push(bytes);
              }
            } catch (e) {}
            return out;
          };
          cx.keys = function () {
            var tables = cx.decoded.filter(function (b) { return b.length === 256; }).slice(0, 3);
            var keys = cx.decoded.filter(function (b) { return b.length !== 256; }).slice(0, 3);
            return tables.length === 3 && keys.length === 3 ? { tables: tables, keys: keys } : null;
          };
          cx.post = function (payload) {
            if (cx.done) return;
            cx.done = true;
            window.$bridge.post(JSON.stringify({ payload: String(payload), keys: cx.keys() }));
          };
          cx.fail = function (message) {
            if (cx.done) return;
            cx.done = true;
            window.$bridge.fail(String(message));
          };
          cx.offer = function (value) {
            if (!cx.match || cx.done || !value || typeof value !== 'object') return;
            try {
              var hit = cx.match(value);
              if (hit) cx.post(JSON.stringify(hit));
            } catch (e) {}
          };
          var nativeParse = JSON.parse;
          JSON.parse = function () {
            var value = nativeParse.apply(this, arguments);
            cx.offer(value);
            return value;
          };
          if (window.Response && Response.prototype.json) {
            var nativeJson = Response.prototype.json;
            Response.prototype.json = function () {
              return nativeJson.apply(this, arguments).then(function (v) { cx.offer(v); return v; });
            };
          }
          cx.scanInitialData = function () {
            try {
              var el = document.getElementById('initial-data');
              if (!el) return;
              var queries = nativeParse(el.textContent).queries || {};
              Object.keys(queries).forEach(function (k) { cx.offer(queries[k]); });
            } catch (e) {}
          };
        })();
    """.trimIndent()

    private companion object {
        const val POLL_MS = 250L
    }
}
