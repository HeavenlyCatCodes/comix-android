package com.comixreader.app.network

import android.content.Context
import android.webkit.WebSettings

/**
 * One User-Agent for OkHttp and every WebView. Cloudflare binds its clearance
 * cookie to the browser that solved the challenge, so these must match.
 */
object UserAgent {
    private const val FALLBACK =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

    @Volatile
    private var systemDefault: String = FALLBACK

    @Volatile
    var override: String? = null

    val value: String get() = override?.takeIf { it.isNotBlank() } ?: systemDefault

    fun init(context: Context) {
        systemDefault = runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: FALLBACK
    }
}
