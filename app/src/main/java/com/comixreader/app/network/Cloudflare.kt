package com.comixreader.app.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Thrown when Cloudflare wants a human. The UI offers a WebView to solve it. */
class CloudflareChallengeException(val url: String) :
    IOException("Cloudflare verification is required")

object CloudflareDetector {
    private val bodyMarkers = listOf(
        "challenge-platform",
        "cf_chl_opt",
        "cf-chl-",
        "cf-turnstile",
        "<title>Just a moment",
        "Attention Required! | Cloudflare",
    )

    fun isChallenge(response: Response): Boolean {
        if (response.code !in setOf(403, 429, 503)) return false
        if (response.header("cf-mitigated").equals("challenge", ignoreCase = true)) return true
        val server = response.header("Server").orEmpty()
        if (!server.contains("cloudflare", ignoreCase = true)) return false
        val body = runCatching { response.peekBody(256 * 1024L).string() }.getOrDefault("")
        return bodyMarkers.any { body.contains(it, ignoreCase = true) }
    }
}

/**
 * Many Cloudflare challenges pass on their own in a real browser engine. This
 * loads the blocked URL in an invisible WebView and waits for a new
 * `cf_clearance` cookie. If none arrives, the caller asks the user instead.
 */
class SilentChallengeSolver(
    private val context: Context,
    private val cookies: WebViewCookieJar,
) {
    private val lock = ReentrantLock()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var lastFailureAt = 0L

    fun clearance(url: String): String? = cookies.get(url, CLEARANCE_COOKIE)

    /**
     * Blocks the calling (background) thread. [seenClearance] is the cookie the
     * failed request carried; if another thread already replaced it we are done.
     */
    fun solve(url: String, seenClearance: String?): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        return lock.withLock {
            val current = clearance(url)
            if (current != null && current != seenClearance) return@withLock true
            if (System.currentTimeMillis() - lastFailureAt < FAILURE_COOLDOWN_MS) return@withLock false

            val solved = runWebView(url, seenClearance)
            if (!solved) lastFailureAt = System.currentTimeMillis()
            solved
        }
    }

    fun resetCooldown() {
        lastFailureAt = 0L
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun runWebView(url: String, seenClearance: String?): Boolean {
        var webView: WebView? = null
        main.post {
            webView = WebView(context).apply {
                WebViewSetup.configure(this)
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        }
        val deadline = System.currentTimeMillis() + SOLVE_TIMEOUT_MS
        var solved = false
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MS)
            val now = clearance(url)
            if (now != null && now != seenClearance) {
                solved = true
                break
            }
        }
        main.post {
            webView?.apply {
                stopLoading()
                destroy()
            }
        }
        if (solved) cookies.flush()
        return solved
    }

    companion object {
        const val CLEARANCE_COOKIE = "cf_clearance"
        private const val SOLVE_TIMEOUT_MS = 20_000L
        private const val POLL_MS = 400L
        private const val FAILURE_COOLDOWN_MS = 60_000L
    }
}

/** Retries a challenged request once after a silent solve, else asks the user. */
class CloudflareInterceptor(private val solver: SilentChallengeSolver) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val seenClearance = solver.clearance(request.url.toString())
        val response = chain.proceed(request)
        if (!CloudflareDetector.isChallenge(response)) return response
        response.close()

        // Challenges for CDN assets are solved on the main site.
        val solveUrl = if (request.url.host == Site.baseHttpUrl.host) request.url.toString() else Site.BASE_URL
        if (solver.solve(solveUrl, seenClearance)) {
            val retry = chain.proceed(request)
            if (!CloudflareDetector.isChallenge(retry)) return retry
            retry.close()
        }
        throw CloudflareChallengeException(solveUrl)
    }
}

object WebViewSetup {
    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = UserAgent.value
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }
}
